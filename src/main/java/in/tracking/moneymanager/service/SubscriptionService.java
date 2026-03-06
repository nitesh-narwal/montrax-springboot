package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.SubscriptionDTO;
import in.tracking.moneymanager.dto.SubscriptionPlanDTO;
import in.tracking.moneymanager.entity.SubscriptionEntity;
import in.tracking.moneymanager.entity.SubscriptionPlanEntity;
import in.tracking.moneymanager.repository.SubscriptionPlanRepository;
import in.tracking.moneymanager.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user subscriptions.
 * Handles plan retrieval, subscription activation, expiry checks, and access control.
 *
 * Subscription Tiers:
 * - FREE: Default for all users, basic features
 * - BASIC: Rs 99/month, AI queries, CSV imports
 * - PREMIUM: Rs 299/month, unlimited features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ProfileService profileService;

    /**
     * Get all available subscription plans for display.
     * Only returns active plans.
     *
     * @return List of available plans
     */
    public List<SubscriptionPlanDTO> getAllPlans() {
        return subscriptionPlanRepository.findByIsActiveTrue()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Get the current user's subscription status.
     * If no active subscription exists, returns FREE plan details.
     * Result is cached to reduce database queries.
     *
     * @return Current subscription details
     */
    @Cacheable(value = "subscription", key = "#root.target.getCurrentProfileId()")
    public SubscriptionDTO getCurrentSubscription() {
        Long profileId = profileService.getCurrentProfile().getId();

        // Try to find active subscription
        Optional<SubscriptionEntity> subscription = subscriptionRepository
                .findByProfileIdAndStatus(profileId, "ACTIVE");

        if (subscription.isPresent()) {
            return toSubscriptionDTO(subscription.get());
        }

        // Check for grace period subscription
        Optional<SubscriptionEntity> graceSubscription = subscriptionRepository
                .findByProfileIdAndStatus(profileId, "GRACE_PERIOD");

        if (graceSubscription.isPresent()) {
            return toSubscriptionDTO(graceSubscription.get());
        }

        // Return FREE plan if no active subscription
        return SubscriptionDTO.builder()
                .planType("FREE")
                .planName("Free Plan")
                .status("ACTIVE")
                .aiQueriesLimit(0)
                .aiQueriesUsed(0)
                .csvImportsLimit(0)
                .csvImportsUsed(0)
                .daysRemaining(999) // Unlimited for free
                .build();
    }

    // Helper method for cache key
    public Long getCurrentProfileId() {
        return profileService.getCurrentProfile().getId();
    }

    /**
     * Activate a subscription after successful payment.
     * Deactivates any existing subscription first.
     *
     * @param profileId User's profile ID
     * @param planId Plan being purchased
     * @param billingCycle MONTHLY or YEARLY
     * @param paymentId Razorpay payment ID for reference
     * @return Activated subscription entity
     */
    @Transactional
    @CacheEvict(value = "subscription", key = "#profileId")
    public SubscriptionEntity activateSubscription(Long profileId, Long planId,
                                                    String billingCycle, String paymentId) {
        // Get the plan details
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found with ID: " + planId));

        // Calculate subscription dates based on billing cycle
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = billingCycle.equalsIgnoreCase("YEARLY")
                ? startDate.plusYears(1)
                : startDate.plusMonths(1);

        // Deactivate any existing active subscription
        subscriptionRepository.findByProfileIdAndStatus(profileId, "ACTIVE")
                .ifPresent(existing -> {
                    existing.setStatus("UPGRADED");
                    subscriptionRepository.save(existing);
                    log.info("Deactivated previous subscription {} for upgrade", existing.getId());
                });

        // Create new subscription
        SubscriptionEntity subscription = SubscriptionEntity.builder()
                .profileId(profileId)
                .planId(planId)
                .planType(plan.getName())
                .startDate(startDate)
                .endDate(endDate)
                .status("ACTIVE")
                .razorpaySubscriptionId(paymentId)
                .autoRenew(true)
                .build();

        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        log.info("Activated {} subscription for profile {} until {}",
                plan.getName(), profileId, endDate);

        return saved;
    }

    /**
     * Check if current user has access to a premium feature.
     * Used by @PremiumFeature aspect for access control.
     *
     * @param requiredPlans Plans that have access to the feature
     * @return true if user has required plan
     */
    public boolean hasAccess(String[] requiredPlans) {
        SubscriptionDTO subscription = getCurrentSubscription();
        String currentPlan = subscription.getPlanType();

        // Check if user's plan is in the required plans list
        for (String plan : requiredPlans) {
            if (plan.equalsIgnoreCase(currentPlan)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancel a user's subscription.
     * Sets status to CANCELLED and disables auto-renew.
     *
     * @param subscriptionId Subscription to cancel
     */
    @Transactional
    public void cancelSubscription(Long subscriptionId) {
        SubscriptionEntity subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        Long profileId = profileService.getCurrentProfile().getId();

        // Verify ownership
        if (!subscription.getProfileId().equals(profileId)) {
            throw new RuntimeException("Access denied - not your subscription");
        }

        subscription.setStatus("CANCELLED");
        subscription.setAutoRenew(false);
        subscriptionRepository.save(subscription);

        log.info("Cancelled subscription {} for profile {}", subscriptionId, profileId);
    }

    /**
     * Scheduled job: Check and update expired subscriptions.
     * Runs daily at midnight IST.
     *
     * - Moves expired ACTIVE subscriptions to GRACE_PERIOD
     * - Moves expired GRACE_PERIOD (>3 days) to EXPIRED
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void checkExpiredSubscriptions() {
        log.info("Running subscription expiry check...");

        LocalDate today = LocalDate.now();

        // Find active subscriptions that have expired
        List<SubscriptionEntity> expired = subscriptionRepository
                .findByStatusAndEndDateBefore("ACTIVE", today);

        for (SubscriptionEntity sub : expired) {
            // Move to grace period (3 days to renew)
            sub.setStatus("GRACE_PERIOD");
            subscriptionRepository.save(sub);
            log.info("Subscription {} moved to grace period", sub.getId());
            // TODO: Send email notification about grace period
        }

        // Find grace period subscriptions older than 3 days
        LocalDate gracePeriodEnd = today.minusDays(3);
        List<SubscriptionEntity> gracePeriodExpired = subscriptionRepository
                .findByStatusAndEndDateBefore("GRACE_PERIOD", gracePeriodEnd);

        for (SubscriptionEntity sub : gracePeriodExpired) {
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);
            log.info("Subscription {} expired after grace period", sub.getId());
            // TODO: Send email notification about expiry
        }

        log.info("Subscription expiry check complete. " +
                "Moved {} to grace, {} expired", expired.size(), gracePeriodExpired.size());
    }

    // ==================== Helper Methods ====================

    /**
     * Convert SubscriptionPlanEntity to DTO.
     */
    private SubscriptionPlanDTO toDTO(SubscriptionPlanEntity entity) {
        return SubscriptionPlanDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .monthlyPrice(entity.getMonthlyPrice())
                .yearlyPrice(entity.getYearlyPrice())
                .maxCategories(entity.getMaxCategories())
                .maxBankImports(entity.getMaxBankImports())
                .aiQueriesPerMonth(entity.getAiQueriesPerMonth())
                .dataRetentionMonths(entity.getDataRetentionMonths())
                .build();
    }

    /**
     * Convert SubscriptionEntity to DTO with plan details.
     */
    private SubscriptionDTO toSubscriptionDTO(SubscriptionEntity entity) {
        // Get plan details for limits
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findById(entity.getPlanId())
                .orElse(null);

        // Calculate days remaining
        int daysRemaining = (int) ChronoUnit.DAYS.between(LocalDate.now(), entity.getEndDate());

        return SubscriptionDTO.builder()
                .id(entity.getId())
                .planType(entity.getPlanType())
                .planName(plan != null ? plan.getDescription() : entity.getPlanType())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(entity.getStatus())
                .autoRenew(entity.getAutoRenew())
                .daysRemaining(Math.max(0, daysRemaining))
                .aiQueriesLimit(plan != null ? plan.getAiQueriesPerMonth() : 0)
                .csvImportsLimit(plan != null ? plan.getMaxBankImports() : 0)
                // Usage will be populated by respective services
                .aiQueriesUsed(0)
                .csvImportsUsed(0)
                .build();
    }
}


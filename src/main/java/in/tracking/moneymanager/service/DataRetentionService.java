package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.entity.SubscriptionEntity;
import in.tracking.moneymanager.entity.SubscriptionPlanEntity;
import in.tracking.moneymanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for automatic data retention management.
 * Cleans up old transaction data based on subscription plan limits.
 *
 * Data Retention Policy:
 * - FREE plan: 3 months retention
 * - BASIC plan: 12 months retention
 * - PREMIUM plan: Unlimited retention (no deletion)
 *
 * The cleanup runs daily at 3 AM to minimize impact on users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ExpenceRepository expenceRepository;
    private final IncomeRepository incomeRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final EmailService emailService;

    // Default retention months if not specified in plan
    private static final int DEFAULT_FREE_RETENTION_MONTHS = 3;
    private static final int DEFAULT_BASIC_RETENTION_MONTHS = 12;

    /**
     * Scheduled job to clean up old data based on subscription plans.
     * Runs daily at 3 AM IST.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupOldData() {
        log.info("========== STARTING DATA RETENTION CLEANUP ==========");

        List<ProfileEntity> allProfiles = profileRepository.findAll();
        int totalProfilesProcessed = 0;
        int totalRecordsDeleted = 0;

        for (ProfileEntity profile : allProfiles) {
            try {
                int recordsDeleted = cleanupDataForProfile(profile);
                if (recordsDeleted > 0) {
                    totalRecordsDeleted += recordsDeleted;
                    log.info("Cleaned up {} records for profile {}", recordsDeleted, profile.getId());
                }
                totalProfilesProcessed++;
            } catch (Exception e) {
                log.error("Error cleaning up data for profile {}: {}", profile.getId(), e.getMessage());
            }
        }

        log.info("========== DATA RETENTION CLEANUP COMPLETE ==========");
        log.info("Profiles processed: {}, Total records deleted: {}", totalProfilesProcessed, totalRecordsDeleted);
    }

    /**
     * Clean up old data for a specific profile based on their subscription plan.
     *
     * @param profile The profile to clean up data for
     * @return Number of records deleted
     */
    @Transactional
    public int cleanupDataForProfile(ProfileEntity profile) {
        Long profileId = profile.getId();

        // Get the user's subscription plan
        int retentionMonths = getRetentionMonthsForProfile(profileId);

        // If retention is 0 or negative, it means unlimited - skip cleanup
        if (retentionMonths <= 0) {
            log.debug("Profile {} has unlimited retention, skipping cleanup", profileId);
            return 0;
        }

        // Calculate the cutoff date
        LocalDate cutoffDate = LocalDate.now().minusMonths(retentionMonths);
        log.debug("Profile {} - Retention: {} months, Cutoff date: {}", profileId, retentionMonths, cutoffDate);

        // Count records to be deleted (for logging)
        long expenseCount = expenceRepository.countByProfileIdAndDateBefore(profileId, cutoffDate);
        long incomeCount = incomeRepository.countByProfileIdAndDateBefore(profileId, cutoffDate);
        long bankTxCount = bankTransactionRepository.countByProfileIdAndTransactionDateBefore(profileId, cutoffDate);

        int totalToDelete = (int) (expenseCount + incomeCount + bankTxCount);

        if (totalToDelete == 0) {
            return 0;
        }

        log.info("Profile {} - Deleting {} expenses, {} incomes, {} bank transactions older than {}",
                profileId, expenseCount, incomeCount, bankTxCount, cutoffDate);

        // Delete old records
        expenceRepository.deleteByProfileIdAndDateBefore(profileId, cutoffDate);
        incomeRepository.deleteByProfileIdAndDateBefore(profileId, cutoffDate);
        bankTransactionRepository.deleteByProfileIdAndTransactionDateBefore(profileId, cutoffDate);

        // Send notification email if significant data was deleted
        if (totalToDelete > 10) {
            sendRetentionNotification(profile, totalToDelete, retentionMonths);
        }

        return totalToDelete;
    }

    /**
     * Get the data retention period in months for a profile based on their subscription.
     *
     * @param profileId The profile ID
     * @return Retention period in months (0 = unlimited)
     */
    public int getRetentionMonthsForProfile(Long profileId) {
        // Find active subscription for the profile
        Optional<SubscriptionEntity> subscription = subscriptionRepository
                .findByProfileIdAndStatus(profileId, "ACTIVE");

        if (subscription.isEmpty()) {
            // Check for grace period subscription
            subscription = subscriptionRepository.findByProfileIdAndStatus(profileId, "GRACE_PERIOD");
        }

        if (subscription.isPresent()) {
            Long planId = subscription.get().getPlanId();
            Optional<SubscriptionPlanEntity> plan = subscriptionPlanRepository.findById(planId);

            if (plan.isPresent() && plan.get().getDataRetentionMonths() != null) {
                return plan.get().getDataRetentionMonths();
            }
            // Fallback based on plan type
            String planType = subscription.get().getPlanType();
            return getDefaultRetentionForPlan(planType);
        }

        // No subscription = FREE plan
        return DEFAULT_FREE_RETENTION_MONTHS;
    }

    /**
     * Get default retention period based on plan name.
     */
    private int getDefaultRetentionForPlan(String planName) {
        if (planName == null) return DEFAULT_FREE_RETENTION_MONTHS;

        return switch (planName.toUpperCase()) {
            case "PREMIUM" -> 0; // Unlimited
            case "BASIC" -> DEFAULT_BASIC_RETENTION_MONTHS;
            default -> DEFAULT_FREE_RETENTION_MONTHS;
        };
    }

    /**
     * Send email notification about data cleanup.
     */
    private void sendRetentionNotification(ProfileEntity profile, int recordsDeleted, int retentionMonths) {
        try {
            String subject = "Money Manager - Old Data Cleaned Up";
            String body = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #333;'>Data Retention Notice</h2>" +
                "<p>Hello %s,</p>" +
                "<p>As per your subscription plan's data retention policy (%d months), we have automatically cleaned up <strong>%d old records</strong> from your account.</p>" +
                "<p>This includes expenses, incomes, and bank transactions older than %d months.</p>" +
                "<h3>Want to keep your data longer?</h3>" +
                "<p>Upgrade to a higher plan for extended data retention:</p>" +
                "<ul>" +
                "<li><strong>Basic Plan:</strong> 12 months data retention</li>" +
                "<li><strong>Premium Plan:</strong> Unlimited data retention</li>" +
                "</ul>" +
                "<p>Visit your subscription page to upgrade.</p>" +
                "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
                "<p style='color: #888; font-size: 12px;'>This is an automated message from Money Manager.</p>" +
                "</div>" +
                "</body></html>",
                profile.getFullname() != null ? profile.getFullname() : "User",
                retentionMonths,
                recordsDeleted,
                retentionMonths
            );

            emailService.sendEmail(profile.getEmail(), subject, body);
            log.debug("Sent data retention notification to {}", profile.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send data retention notification to {}: {}", profile.getEmail(), e.getMessage());
        }
    }

    /**
     * Get data retention info for the current user (for display in UI).
     *
     * @param profileId The profile ID
     * @return Data retention information
     */
    public DataRetentionInfo getRetentionInfo(Long profileId) {
        int retentionMonths = getRetentionMonthsForProfile(profileId);
        LocalDate cutoffDate = retentionMonths > 0 ? LocalDate.now().minusMonths(retentionMonths) : null;

        long expenseCount = 0;
        long incomeCount = 0;
        long bankTxCount = 0;

        if (cutoffDate != null) {
            expenseCount = expenceRepository.countByProfileIdAndDateBefore(profileId, cutoffDate);
            incomeCount = incomeRepository.countByProfileIdAndDateBefore(profileId, cutoffDate);
            bankTxCount = bankTransactionRepository.countByProfileIdAndTransactionDateBefore(profileId, cutoffDate);
        }

        return new DataRetentionInfo(
                retentionMonths,
                retentionMonths <= 0,
                cutoffDate,
                expenseCount + incomeCount + bankTxCount
        );
    }

    /**
     * Record class for data retention information.
     */
    public record DataRetentionInfo(
            int retentionMonths,
            boolean isUnlimited,
            LocalDate nextCleanupCutoffDate,
            long recordsToBeDeleted
    ) {}
}


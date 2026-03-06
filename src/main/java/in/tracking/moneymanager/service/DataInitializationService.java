package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.SubscriptionPlanEntity;
import in.tracking.moneymanager.repository.SubscriptionPlanRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service to initialize default data on application startup.
 * Creates subscription plans if they don't exist.
 *
 * Plans created:
 * - FREE: Basic features, 5 categories, 3 months data retention
 * - BASIC: Rs 99/month, 5 AI queries, 3 CSV imports, 12 months data retention
 * - PREMIUM: Rs 299/month, 50 AI queries, unlimited CSV imports, unlimited data retention
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService {

    private final SubscriptionPlanRepository planRepository;

    /**
     * Initialize subscription plans on application startup.
     * Only creates plans if they don't already exist in database.
     */
    @PostConstruct
    public void initializeSubscriptionPlans() {
        // Skip if plans already exist
        if (planRepository.count() > 0) {
            log.info("Subscription plans already exist, skipping initialization");
            updateExistingPlansWithRetention(); // Update existing plans with retention if missing
            return;
        }

        log.info("Initializing subscription plans...");

        // ==================== FREE Plan ====================
        // Default plan for all users
        planRepository.save(SubscriptionPlanEntity.builder()
                .name("FREE")
                .description("Free Plan - Basic Features")
                .monthlyPrice(BigDecimal.ZERO)
                .yearlyPrice(BigDecimal.ZERO)
                .maxCategories(5)             // Limited categories
                .maxBankImports(0)            // No CSV imports
                .aiQueriesPerMonth(0)         // No AI features
                .dataRetentionMonths(3)       // 3 months data retention
                .isActive(true)
                .build());
        log.info("Created FREE plan with 3 months data retention");

        // ==================== BASIC Plan ====================
        // Entry-level paid plan
        planRepository.save(SubscriptionPlanEntity.builder()
                .name("BASIC")
                .description("Basic Plan - Essential Features")
                .monthlyPrice(BigDecimal.valueOf(99))
                .yearlyPrice(BigDecimal.valueOf(999))  // ~17% discount
                .maxCategories(999)           // Unlimited (using high number)
                .maxBankImports(3)            // 3 CSV imports per month
                .aiQueriesPerMonth(5)         // 5 AI queries per month
                .dataRetentionMonths(12)      // 12 months data retention
                .isActive(true)
                .build());
        log.info("Created BASIC plan with 12 months data retention");

        // ==================== PREMIUM Plan ====================
        // Full-featured plan
        planRepository.save(SubscriptionPlanEntity.builder()
                .name("PREMIUM")
                .description("Premium Plan - All Features Unlocked")
                .monthlyPrice(BigDecimal.valueOf(299))
                .yearlyPrice(BigDecimal.valueOf(2999))  // ~17% discount
                .maxCategories(999)           // Unlimited
                .maxBankImports(999)          // Unlimited
                .aiQueriesPerMonth(50)        // 50 AI queries per month
                .dataRetentionMonths(0)       // 0 = Unlimited data retention
                .isActive(true)
                .build());
        log.info("Created PREMIUM plan with unlimited data retention");

        log.info("Subscription plans initialized successfully!");
    }

    /**
     * Update existing plans with data retention values if they don't have them.
     * This ensures existing databases get the new retention feature.
     */
    private void updateExistingPlansWithRetention() {
        planRepository.findAll().forEach(plan -> {
            if (plan.getDataRetentionMonths() == null) {
                switch (plan.getName().toUpperCase()) {
                    case "FREE" -> plan.setDataRetentionMonths(3);
                    case "BASIC" -> plan.setDataRetentionMonths(12);
                    case "PREMIUM" -> plan.setDataRetentionMonths(0); // Unlimited
                    default -> plan.setDataRetentionMonths(3); // Default to FREE
                }
                planRepository.save(plan);
                log.info("Updated {} plan with {} months data retention",
                        plan.getName(), plan.getDataRetentionMonths());
            }
        });
    }
}


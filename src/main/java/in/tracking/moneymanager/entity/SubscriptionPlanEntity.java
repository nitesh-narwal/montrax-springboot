package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing subscription plans available in the system.
 * Plans: FREE, BASIC, PREMIUM (3 tiers only)
 *
 * FREE - Basic features, 5 categories max, 3 months data retention
 * BASIC - Rs 99/month, AI queries, CSV import, 12 months data retention
 * PREMIUM - Rs 299/month, Unlimited features, unlimited data retention
 */
@Entity
@Table(name = "tbl_subscription_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plan name: FREE, BASIC, PREMIUM
    @Column(nullable = false, unique = true)
    private String name;

    // Human-readable description of the plan
    private String description;

    // Monthly subscription price in INR
    @Column(name = "monthly_price")
    private BigDecimal monthlyPrice;

    // Yearly subscription price in INR (discounted)
    @Column(name = "yearly_price")
    private BigDecimal yearlyPrice;

    // Currency code (default: INR)
    @Column(length = 3)
    @Builder.Default
    private String currency = "INR";

    // Maximum categories allowed for this plan
    @Column(name = "max_categories")
    private Integer maxCategories;

    // Maximum CSV imports per month
    @Column(name = "max_bank_imports")
    private Integer maxBankImports;

    // Maximum AI queries per month
    @Column(name = "ai_queries_per_month")
    private Integer aiQueriesPerMonth;

    // Data retention period in months (0 or null = unlimited)
    // FREE: 3 months, BASIC: 12 months, PREMIUM: 0 (unlimited)
    @Column(name = "data_retention_months")
    @Builder.Default
    private Integer dataRetentionMonths = 0;

    // Whether this plan is currently active/available
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // Timestamp when plan was created
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}


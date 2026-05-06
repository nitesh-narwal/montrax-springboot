package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing subscription plans available in the system.
 * Plans: FREE, BASIC, PREMIUM (3 tiers only)
 *
 * FREE - Basic features, 5 categories max, 3 months data retention
 * BASIC - Rs 99/month, AI queries, CSV import, 12 months data retention
 * PREMIUM - Rs 299/month, Unlimited features, unlimited data retention
 *
 * This is a master data table - mostly static with few updates.
 */
@Entity
@Table(name = "tbl_subscription_plans",
        indexes = {
                @Index(name = "idx_plan_name", columnList = "name", unique = true),
                @Index(name = "idx_plan_is_active", columnList = "is_active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {})
public class SubscriptionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "plan_seq")
    @SequenceGenerator(name = "plan_seq", sequenceName = "seq_subscription_plans", allocationSize = 1)
    private Long id;

    // Plan name: FREE, BASIC, PREMIUM (unique identifier)
    @Column(name = "name", length = 50, nullable = false, unique = true)
    private String name;

    // Human-readable description of the plan
    @Column(name = "description", length = 500)
    private String description;

    // Display name for UI
    @Column(name = "display_name", length = 100)
    private String displayName;

    // Monthly subscription price in INR
    @Column(name = "monthly_price", precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    // Yearly subscription price in INR (discounted)
    @Column(name = "yearly_price", precision = 10, scale = 2)
    private BigDecimal yearlyPrice;

    // Currency code (default: INR)
    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "INR";

    // Maximum categories allowed for this plan
    @Column(name = "max_categories", nullable = false)
    private Integer maxCategories;

    // Maximum CSV imports per month
    @Column(name = "max_bank_imports", nullable = false)
    private Integer maxBankImports;

    // Maximum AI queries per month
    @Column(name = "ai_queries_per_month", nullable = false)
    private Integer aiQueriesPerMonth;

    // Data retention period in months (0 or null = unlimited)
    // FREE: 3 months, BASIC: 12 months, PREMIUM: 0 (unlimited)
    @Column(name = "data_retention_months", nullable = false)
    @Builder.Default
    private Integer dataRetentionMonths = 0;

    // Razorpay plan ID for this subscription
    @Column(name = "razorpay_plan_id", length = 100)
    private String razorpayPlanId;

    // Whether this plan is currently active/available
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Display order for listing plans
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    // Timestamp when plan was created
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Timestamp when plan was last updated
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }
}
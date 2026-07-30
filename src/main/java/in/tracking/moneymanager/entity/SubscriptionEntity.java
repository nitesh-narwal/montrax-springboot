package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a user's active subscription.
 * Links a profile to a subscription plan with validity dates.
 *
 * Status can be: ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD
 * Grace period is 3-7 days after expiry before downgrading to FREE
 *
 * Uses JPA relationships for better ORM benefits and data integrity.
 */
@Entity
@Table(name = "tbl_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                columnNames = "profile_id",
                name = "uk_subscription_profile_active"
        ),
        indexes = {
                @Index(name = "idx_subscription_profile_id", columnList = "profile_id"),
                @Index(name = "idx_subscription_plan_id", columnList = "plan_id"),
                @Index(name = "idx_subscription_status", columnList = "status"),
                @Index(name = "idx_subscription_end_date", columnList = "end_date"),
                @Index(name = "idx_subscription_status_end_date", columnList = "status,end_date")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"profile", "plan"})
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subscription_seq")
    @SequenceGenerator(name = "subscription_seq", sequenceName = "seq_subscriptions", allocationSize = 1)
    private Long id;

    // Reference to the user's profile using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscription_profile"))
    private ProfileEntity profile;

    // Reference to the subscription plan using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscription_plan"))
    private SubscriptionPlanEntity plan;

    // Plan type for quick access: FREE, BASIC, PREMIUM (denormalized for performance)
    @Column(name = "plan_type", length = 20, nullable = false)
    private String planType;

    // Subscription start date
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Subscription end date
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // Status: ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    // Razorpay subscription ID for recurring payments
    @Column(name = "razorpay_subscription_id", length = 100)
    private String razorpaySubscriptionId;

    // Whether to auto-renew the subscription
    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = true;

    // Grace period end date (for GRACE_PERIOD status)
    @Column(name = "grace_period_until")
    private LocalDate gracePeriodUntil;

    // Reason for subscription cancellation (if cancelled)
    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getPlanId() {
        return plan.getId();
    }
}
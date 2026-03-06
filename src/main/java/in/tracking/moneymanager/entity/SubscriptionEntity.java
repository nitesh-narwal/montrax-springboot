package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a user's active subscription.
 * Links a profile to a subscription plan with validity dates.
 *
 * Status can be: ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD
 * Grace period is 3 days after expiry before downgrading to FREE
 */
@Entity
@Table(name = "tbl_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to the user's profile
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    // Reference to the subscription plan
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    // Plan type for quick access: FREE, BASIC, PREMIUM
    @Column(name = "plan_type", nullable = false)
    private String planType;

    // Subscription start date
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Subscription end date
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // Status: ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD
    @Builder.Default
    private String status = "ACTIVE";

    // Razorpay subscription ID for recurring payments
    @Column(name = "razorpay_subscription_id")
    private String razorpaySubscriptionId;

    // Whether to auto-renew the subscription
    @Column(name = "auto_renew")
    @Builder.Default
    private Boolean autoRenew = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Pre-update hook to set updated timestamp automatically
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}


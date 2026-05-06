package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity to track all payment transactions.
 * Stores Razorpay payment details for audit and reconciliation.
 *
 * Status: SUCCESS, FAILED, PENDING, REFUNDED
 * Payment methods: UPI, CARD, NETBANKING, WALLET
 *
 * This is an immutable audit log table - no updates after creation.
 */
@Entity
@Table(name = "tbl_payment_history",
        indexes = {
                @Index(name = "idx_payment_profile_id", columnList = "profile_id"),
                @Index(name = "idx_payment_subscription_id", columnList = "subscription_id"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_razorpay_order", columnList = "razorpay_order_id"),
                @Index(name = "idx_payment_razorpay_payment", columnList = "razorpay_payment_id"),
                @Index(name = "idx_payment_created_at", columnList = "created_at"),
                @Index(name = "idx_payment_profile_status", columnList = "profile_id,status"),
                @Index(name = "idx_payment_profile_created", columnList = "profile_id,created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"profile", "subscription"})
public class PaymentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_hist_seq")
    @SequenceGenerator(name = "payment_hist_seq", sequenceName = "seq_payment_history", allocationSize = 1)
    private Long id;

    // User who made the payment - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_profile"))
    private ProfileEntity profile;

    // Subscription this payment is for - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "subscription_id", nullable = true, foreignKey = @ForeignKey(name = "fk_payment_subscription"))
    private SubscriptionEntity subscription;

    // Razorpay order ID (starts with order_)
    @Column(name = "razorpay_order_id", length = 100, nullable = false)
    private String razorpayOrderId;

    // Razorpay payment ID (starts with pay_)
    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    // Razorpay signature for verification
    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    // Payment amount in INR
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Currency code (INR)
    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "INR";

    // Payment status: SUCCESS, FAILED, PENDING, REFUNDED
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    // Payment method: UPI, CARD, NETBANKING, WALLET
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    // Reason for failure (if status is FAILED)
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Refund ID (if refunded)
    @Column(name = "refund_id", length = 100)
    private String refundId;

    // Refund amount (if partially refunded)
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    // Refund reason (if refunded)
    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    // Original order notes/description
    @Column(name = "notes", length = 500)
    private String notes;

    // Whether this payment has been reconciled with bank
    @Column(name = "is_reconciled", nullable = false)
    @Builder.Default
    private Boolean isReconciled = false;

    // Retry count (for failed payments)
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.isReconciled == null) {
            this.isReconciled = false;
        }
    }
}
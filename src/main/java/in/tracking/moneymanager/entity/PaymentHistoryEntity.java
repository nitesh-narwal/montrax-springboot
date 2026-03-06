package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity to track all payment transactions.
 * Stores Razorpay payment details for audit and reconciliation.
 *
 * Status: SUCCESS, FAILED, PENDING, REFUNDED
 * Payment methods: UPI, CARD, NETBANKING, WALLET
 */
@Entity
@Table(name = "tbl_payment_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User who made the payment
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    // Subscription this payment is for
    @Column(name = "subscription_id")
    private Long subscriptionId;

    // Razorpay order ID (starts with order_)
    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    // Razorpay payment ID (starts with pay_)
    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    // Razorpay signature for verification
    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    // Payment amount in INR
    @Column(nullable = false)
    private BigDecimal amount;

    // Currency code (INR)
    @Column(length = 3)
    @Builder.Default
    private String currency = "INR";

    // Payment status: SUCCESS, FAILED, PENDING, REFUNDED
    @Column(nullable = false)
    private String status;

    // Payment method: UPI, CARD, NETBANKING, WALLET
    @Column(name = "payment_method")
    private String paymentMethod;

    // Reason for failure (if status is FAILED)
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}


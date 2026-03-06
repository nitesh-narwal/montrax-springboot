package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.PaymentHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for payment history operations.
 * Used for tracking payments and verification.
 */
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistoryEntity, Long> {

    // Get payment history for a user, most recent first
    List<PaymentHistoryEntity> findByProfileIdOrderByCreatedAtDesc(Long profileId);

    // Find payment by Razorpay order ID (for verification)
    Optional<PaymentHistoryEntity> findByRazorpayOrderId(String orderId);

    // Find payment by Razorpay payment ID
    Optional<PaymentHistoryEntity> findByRazorpayPaymentId(String paymentId);

    // Delete all payment history for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);
}


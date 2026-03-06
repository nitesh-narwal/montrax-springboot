package in.tracking.moneymanager.controller;

import com.razorpay.RazorpayException;
import in.tracking.moneymanager.dto.PaymentOrderDTO;
import in.tracking.moneymanager.dto.PaymentVerifyDTO;
import in.tracking.moneymanager.entity.PaymentHistoryEntity;
import in.tracking.moneymanager.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for payment operations.
 * Handles Razorpay order creation, payment verification, and history retrieval.
 *
 * Endpoints:
 * - POST /api/payments/create-order - Create Razorpay order
 * - POST /api/payments/verify - Verify payment after completion
 * - GET /api/payments/history - Get payment history
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Create a Razorpay order for subscription purchase.
     * Frontend uses the returned order ID to initiate Razorpay checkout.
     *
     * @param requestBody JSON containing planName (FREE, BASIC, PREMIUM) and billingCycle (MONTHLY/YEARLY)
     * @return Razorpay order details
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, String> requestBody) {
        try {
            String planName = requestBody.get("planName");
            String billingCycle = requestBody.getOrDefault("billingCycle", "MONTHLY");

            if (planName == null || planName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "planName is required"
                ));
            }

            PaymentOrderDTO order = paymentService.createOrderByPlanName(planName, billingCycle);
            return ResponseEntity.ok(order);
        } catch (RazorpayException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to create Razorpay order: " + e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Verify payment after Razorpay callback.
     * Called by frontend after user completes payment on Razorpay.
     * On success, activates the subscription.
     *
     * @param verifyDTO Razorpay callback parameters (orderId, paymentId, signature)
     * @return Success/failure response
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody PaymentVerifyDTO verifyDTO) {
        boolean success = paymentService.verifyPaymentFromOrder(verifyDTO);

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment verified and subscription activated successfully!"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Payment verification failed. Please contact support."
            ));
        }
    }

    /**
     * Get payment history for current user.
     * Shows all payment transactions including pending and failed.
     *
     * @return List of payment transactions
     */
    @GetMapping("/history")
    public ResponseEntity<List<PaymentHistoryEntity>> getPaymentHistory() {
        return ResponseEntity.ok(paymentService.getPaymentHistory());
    }

    /**
     * Delete a specific payment history record.
     * Only PENDING or FAILED payments can be deleted, not SUCCESS ones.
     *
     * @param id Payment history ID to delete
     * @return Success/failure response
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Map<String, Object>> deletePaymentHistory(@PathVariable Long id) {
        boolean deleted = paymentService.deletePaymentHistory(id);

        if (deleted) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment history deleted successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Cannot delete this payment record. Only PENDING or FAILED payments can be deleted."
            ));
        }
    }

    /**
     * Delete all PENDING and FAILED payment history records.
     * Successful payments are preserved for records.
     *
     * @return Number of deleted records
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, Object>> deleteAllPaymentHistory() {
        int deleted = paymentService.deleteAllPaymentHistory();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Deleted " + deleted + " payment history record(s)",
            "deletedCount", deleted
        ));
    }

    /**
     * Clear ALL payment history including successful ones.
     * Use with caution - this is for danger zone / account cleanup.
     *
     * @return Number of deleted records
     */
    @DeleteMapping("/history/all")
    public ResponseEntity<Map<String, Object>> clearAllPaymentHistory() {
        int deleted = paymentService.clearAllPaymentHistory();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Cleared all " + deleted + " payment history record(s)",
            "deletedCount", deleted
        ));
    }
}


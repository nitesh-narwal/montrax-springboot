package in.tracking.moneymanager.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import in.tracking.moneymanager.dto.PaymentOrderDTO;
import in.tracking.moneymanager.dto.PaymentVerifyDTO;
import in.tracking.moneymanager.entity.PaymentHistoryEntity;
import in.tracking.moneymanager.entity.SubscriptionPlanEntity;
import in.tracking.moneymanager.repository.PaymentHistoryRepository;
import in.tracking.moneymanager.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for handling Razorpay payment operations.
 * Creates orders, verifies payments, and records transactions.
 *
 * Payment Flow:
 * 1. Frontend calls createOrder() to get Razorpay order
 * 2. User completes payment on Razorpay checkout
 * 3. Frontend calls verifyPayment() with callback parameters
 * 4. On success, subscription is activated
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionService subscriptionService;
    private final ProfileService profileService;

    // Razorpay public key (safe to expose to frontend)
    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    // Razorpay key secret for signature verification (checkout)
    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    // Webhook secret for verifying Razorpay webhooks (optional, different from key secret)
    @Value("${razorpay.webhook.secret:${razorpay.key.secret}}")
    private String webhookSecret;

    /**
     * Create a Razorpay order for subscription purchase by plan NAME.
     * This is the new method that frontend calls with planName (FREE, BASIC, PREMIUM).
     *
     * @param planName Name of the subscription plan (FREE, BASIC, PREMIUM)
     * @param billingCycle MONTHLY or YEARLY
     * @return Order details including Razorpay order ID
     */
    public PaymentOrderDTO createOrderByPlanName(String planName, String billingCycle) throws RazorpayException {
        // Get plan by name
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findByName(planName)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planName));

        return createOrder(plan.getId(), billingCycle);
    }

    /**
     * Create a Razorpay order for subscription purchase.
     * Returns order details for frontend to initiate payment.
     *
     * @param planId ID of the subscription plan
     * @param billingCycle MONTHLY or YEARLY
     * @return Order details including Razorpay order ID
     */
    public PaymentOrderDTO createOrder(Long planId, String billingCycle) throws RazorpayException {
        // Get plan details
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found with ID: " + planId));

        // Don't allow payment for FREE plan
        if ("FREE".equalsIgnoreCase(plan.getName())) {
            throw new RuntimeException("Cannot create payment order for FREE plan");
        }

        // Determine price based on billing cycle
        BigDecimal price = billingCycle.equalsIgnoreCase("YEARLY")
                ? plan.getYearlyPrice()
                : plan.getMonthlyPrice();

        // Razorpay expects amount in smallest currency unit (paise for INR)
        int amountInPaise = price.multiply(BigDecimal.valueOf(100)).intValue();

        // Create Razorpay order
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "rcpt_" + System.currentTimeMillis());

        // Add notes for reference (store plan info for verification)
        JSONObject notes = new JSONObject();
        notes.put("plan_id", planId.toString());
        notes.put("plan_name", plan.getName());
        notes.put("billing_cycle", billingCycle);
        orderRequest.put("notes", notes);

        Order order = razorpayClient.orders.create(orderRequest);
        String orderId = order.get("id");

        log.info("Created Razorpay order: {} for plan: {} ({})", orderId, plan.getName(), billingCycle);

        // Record pending payment in database with plan info
        Long profileId = profileService.getCurrentProfile().getId();
        PaymentHistoryEntity payment = PaymentHistoryEntity.builder()
                .profileId(profileId)
                .razorpayOrderId(orderId)
                .amount(price)
                .currency("INR")
                .status("PENDING")
                .paymentMethod(plan.getName() + "_" + billingCycle) // Store plan info temporarily
                .build();
        paymentHistoryRepository.save(payment);

        // Return order details for frontend
        return PaymentOrderDTO.builder()
                .orderId(orderId)
                .amount(price)
                .currency("INR")
                .razorpayKeyId(razorpayKeyId)
                .planName(plan.getName())
                .billingCycle(billingCycle)
                .build();
    }

    /**
     * Verify payment after Razorpay callback.
     * Retrieves plan info from the stored payment record.
     *
     * @param verifyDTO Payment verification parameters from Razorpay
     * @return true if payment verified and subscription activated
     */
    @Transactional
    public boolean verifyPaymentFromOrder(PaymentVerifyDTO verifyDTO) {
        try {
            // Get the payment record to retrieve plan info
            PaymentHistoryEntity payment = paymentHistoryRepository
                    .findByRazorpayOrderId(verifyDTO.getRazorpayOrderId())
                    .orElseThrow(() -> new RuntimeException("Payment record not found"));

            // Extract plan info from paymentMethod field (format: PLANNAME_BILLINGCYCLE)
            String[] planInfo = payment.getPaymentMethod().split("_");
            String planName = planInfo[0];
            String billingCycle = planInfo.length > 1 ? planInfo[1] : "MONTHLY";

            // Get plan ID
            SubscriptionPlanEntity plan = subscriptionPlanRepository.findByName(planName)
                    .orElseThrow(() -> new RuntimeException("Plan not found: " + planName));

            return verifyPayment(verifyDTO, plan.getId(), billingCycle);
        } catch (Exception e) {
            log.error("Payment verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verify payment after Razorpay callback.
     * Validates signature and activates subscription on success.
     *
     * @param verifyDTO Payment verification parameters from Razorpay
     * @param planId Plan being purchased
     * @param billingCycle MONTHLY or YEARLY
     * @return true if payment verified and subscription activated
     */
    @Transactional
    public boolean verifyPayment(PaymentVerifyDTO verifyDTO, Long planId, String billingCycle) {
        try {
            // Create signature verification string
            String data = verifyDTO.getRazorpayOrderId() + "|" + verifyDTO.getRazorpayPaymentId();

            log.info("Verifying payment signature for order: {}", verifyDTO.getRazorpayOrderId());

            // Verify signature using Razorpay utility (MUST use key secret, not webhook secret)
            boolean isValid = Utils.verifySignature(data, verifyDTO.getRazorpaySignature(),
                    razorpayKeySecret);

            if (!isValid) {
                log.error("Payment signature verification failed for order: {}. Data: {}",
                        verifyDTO.getRazorpayOrderId(), data);
                updatePaymentStatus(verifyDTO.getRazorpayOrderId(), "FAILED", "Signature mismatch");
                return false;
            }

            log.info("Payment signature verified successfully for order: {}", verifyDTO.getRazorpayOrderId());

            // Update payment record with success details
            PaymentHistoryEntity payment = paymentHistoryRepository
                    .findByRazorpayOrderId(verifyDTO.getRazorpayOrderId())
                    .orElseThrow(() -> new RuntimeException("Payment record not found"));

            payment.setRazorpayPaymentId(verifyDTO.getRazorpayPaymentId());
            payment.setRazorpaySignature(verifyDTO.getRazorpaySignature());
            payment.setStatus("SUCCESS");
            payment.setPaymentMethod("RAZORPAY"); // Reset to actual payment method
            paymentHistoryRepository.save(payment);

            // Activate subscription for the user
            subscriptionService.activateSubscription(
                    payment.getProfileId(),
                    planId,
                    billingCycle,
                    verifyDTO.getRazorpayPaymentId()
            );

            log.info("Payment verified and subscription activated. Order: {}, Profile: {}",
                    verifyDTO.getRazorpayOrderId(), payment.getProfileId());
            return true;

        } catch (RazorpayException e) {
            log.error("Razorpay verification error: {}", e.getMessage());
            updatePaymentStatus(verifyDTO.getRazorpayOrderId(), "FAILED", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Payment verification failed: {}", e.getMessage());
            updatePaymentStatus(verifyDTO.getRazorpayOrderId(), "FAILED", e.getMessage());
            return false;
        }
    }

    /**
     * Get payment history for current user.
     *
     * @return List of payment transactions
     */
    public List<PaymentHistoryEntity> getPaymentHistory() {
        Long profileId = profileService.getCurrentProfile().getId();
        return paymentHistoryRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
    }

    /**
     * Delete a specific payment history record.
     * Only allows deleting PENDING or FAILED payments, not SUCCESS ones.
     *
     * @param paymentId Payment history ID to delete
     * @return true if deleted, false if not found or not allowed
     */
    @Transactional
    public boolean deletePaymentHistory(Long paymentId) {
        Long profileId = profileService.getCurrentProfile().getId();

        return paymentHistoryRepository.findById(paymentId)
                .filter(payment -> payment.getProfileId().equals(profileId))
                .filter(payment -> !"SUCCESS".equals(payment.getStatus())) // Don't delete successful payments
                .map(payment -> {
                    paymentHistoryRepository.delete(payment);
                    log.info("Deleted payment history {} for profile {}", paymentId, profileId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Delete all payment history for current user (except successful ones).
     *
     * @return Number of records deleted
     */
    @Transactional
    public int deleteAllPaymentHistory() {
        Long profileId = profileService.getCurrentProfile().getId();
        List<PaymentHistoryEntity> payments = paymentHistoryRepository
                .findByProfileIdOrderByCreatedAtDesc(profileId);

        int deleted = 0;
        for (PaymentHistoryEntity payment : payments) {
            // Only delete PENDING or FAILED payments
            if (!"SUCCESS".equals(payment.getStatus())) {
                paymentHistoryRepository.delete(payment);
                deleted++;
            }
        }

        log.info("Deleted {} payment history records for profile {}", deleted, profileId);
        return deleted;
    }

    /**
     * Clear all payment history including successful ones (admin/danger zone).
     *
     * @return Number of records deleted
     */
    @Transactional
    public int clearAllPaymentHistory() {
        Long profileId = profileService.getCurrentProfile().getId();
        List<PaymentHistoryEntity> payments = paymentHistoryRepository
                .findByProfileIdOrderByCreatedAtDesc(profileId);

        int count = payments.size();
        paymentHistoryRepository.deleteAll(payments);

        log.info("Cleared all {} payment history records for profile {}", count, profileId);
        return count;
    }

    /**
     * Update payment status (helper method).
     *
     * @param orderId Razorpay order ID
     * @param status New status
     * @param reason Failure reason (if applicable)
     */
    private void updatePaymentStatus(String orderId, String status, String reason) {
        paymentHistoryRepository.findByRazorpayOrderId(orderId)
                .ifPresent(payment -> {
                    payment.setStatus(status);
                    payment.setFailureReason(reason);
                    paymentHistoryRepository.save(payment);
                });
    }
}


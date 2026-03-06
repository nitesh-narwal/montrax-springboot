package in.tracking.moneymanager.dto;

import lombok.*;

/**
 * DTO for payment verification request from frontend.
 * Contains Razorpay callback parameters after payment completion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyDTO {

    // Order ID from Razorpay (order_xxxxx)
    private String razorpayOrderId;

    // Payment ID from Razorpay (pay_xxxxx)
    private String razorpayPaymentId;

    // Signature for verification
    private String razorpaySignature;
}


package in.tracking.moneymanager.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for Razorpay order creation response.
 * Frontend uses this to initiate the Razorpay payment flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrderDTO {

    // Razorpay order ID (order_xxxxx)
    private String orderId;

    // Amount in rupees (not paise)
    private BigDecimal amount;

    // Currency code
    private String currency;

    // Razorpay public key for frontend
    private String razorpayKeyId;

    // Plan being purchased
    private String planName;

    // Billing cycle: MONTHLY or YEARLY
    private String billingCycle;
}


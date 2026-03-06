package in.tracking.moneymanager.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Razorpay payment gateway.
 * Initializes the Razorpay client with API credentials from environment.
 *
 * Get your keys from: https://dashboard.razorpay.com/app/keys
 */
@Configuration
public class RazorpayConfig {

    // Razorpay Key ID from environment variable
    @Value("${razorpay.key.id}")
    private String keyId;

    // Razorpay Key Secret from environment variable
    @Value("${razorpay.key.secret}")
    private String keySecret;

    /**
     * Creates and configures the Razorpay client bean.
     * This client is used for all Razorpay API operations like:
     * - Creating orders
     * - Verifying payments
     * - Processing refunds
     */
    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        return new RazorpayClient(keyId, keySecret);
    }
}


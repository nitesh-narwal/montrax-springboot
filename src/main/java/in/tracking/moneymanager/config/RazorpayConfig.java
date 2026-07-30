package in.tracking.moneymanager.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import in.tracking.moneymanager.service.AppCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RazorpayConfig {

    private final AppCacheService appCacheService;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        String keyId = appCacheService.get("razorpay.key.id");
        String keySecret = appCacheService.get("razorpay.key.secret");
        return new RazorpayClient(keyId, keySecret);
    }
}


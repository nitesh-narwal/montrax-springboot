package in.tracking.moneymanager.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import in.tracking.moneymanager.service.AppCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CloudinaryConfig {

    private final AppCacheService appCacheService;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", appCacheService.get("cloudinary.cloud-name"),
                "api_key", appCacheService.get("cloudinary.api-key"),
                "api_secret", appCacheService.get("cloudinary.api-secret"),
                "secure", true
        ));
    }
}


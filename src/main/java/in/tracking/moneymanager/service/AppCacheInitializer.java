package in.tracking.moneymanager.service;

import in.tracking.moneymanager.document.AppConfigDocument;
import in.tracking.moneymanager.repository.mongo.AppConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(1)
@Slf4j
public class AppCacheInitializer implements CommandLineRunner {

    @Autowired(required = false)
    private AppConfigRepository appConfigRepository;

    private final Environment environment;

    public AppCacheInitializer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        if (appConfigRepository == null) {
            log.info("MongoDB not available. Skipping AppCache seeding.");
            return;
        }

        if (appConfigRepository.count() > 0) {
            log.info("AppCache already seeded with {} entries. Skipping.", appConfigRepository.count());
            return;
        }

        log.info("Seeding AppCache from environment variables...");

        Map<String, ConfigEntry> configEntries = new LinkedHashMap<>();

        // JWT
        configEntries.put("jwt.secret", new ConfigEntry("jwt", "JWT signing secret key", true));
        configEntries.put("jwt.expiration", new ConfigEntry("jwt", "JWT token expiration in milliseconds", false));

        // Frontend
        configEntries.put("money.manager.frontend.url", new ConfigEntry("frontend", "Frontend application URL", false));

        // Admin seed
        configEntries.put("admin.email", new ConfigEntry("admin", "Initial admin account email", false));
        configEntries.put("admin.password", new ConfigEntry("admin", "Initial admin account password", true));

        // TextBee OTP
        configEntries.put("textbee.api.key", new ConfigEntry("textbee", "TextBee API key", true));
        configEntries.put("textbee.device.id", new ConfigEntry("textbee", "TextBee gateway device id", false));
        configEntries.put("textbee.api.url", new ConfigEntry("textbee", "TextBee gateway base URL", false));

        // Razorpay
        configEntries.put("razorpay.key.id", new ConfigEntry("razorpay", "Razorpay API key ID", true));
        configEntries.put("razorpay.key.secret", new ConfigEntry("razorpay", "Razorpay API key secret", true));
        configEntries.put("razorpay.webhook.secret", new ConfigEntry("razorpay", "Razorpay webhook secret", true));

        // Gemini AI
        configEntries.put("gemini.api.key", new ConfigEntry("gemini", "Google Gemini API key", true));
        configEntries.put("gemini.model", new ConfigEntry("gemini", "Gemini AI model name", false));
        configEntries.put("gemini.api.url", new ConfigEntry("gemini", "Gemini API base URL", false));

        // Cloudinary
        configEntries.put("cloudinary.cloud-name", new ConfigEntry("cloudinary", "Cloudinary cloud name", false));
        configEntries.put("cloudinary.api-key", new ConfigEntry("cloudinary", "Cloudinary API key", true));
        configEntries.put("cloudinary.api-secret", new ConfigEntry("cloudinary", "Cloudinary API secret", true));

        // Mail
        configEntries.put("spring.mail.username", new ConfigEntry("mail", "Mailjet SMTP API key", true));
        configEntries.put("spring.mail.password", new ConfigEntry("mail", "Mailjet SMTP secret key", true));
        configEntries.put("spring.mail.properties.mail.smtp.from", new ConfigEntry("mail", "Email sender address", false));

        int seeded = 0;
        for (Map.Entry<String, ConfigEntry> entry : configEntries.entrySet()) {
            String key = entry.getKey();
            ConfigEntry meta = entry.getValue();
            String value = environment.getProperty(key);

            if (value != null && !value.isBlank()) {
                appConfigRepository.save(AppConfigDocument.builder()
                        .configKey(key)
                        .configValue(value)
                        .category(meta.category)
                        .description(meta.description)
                        .isSecret(meta.isSecret)
                        .updatedAt(LocalDateTime.now())
                        .updatedBy("system-init")
                        .build());
                seeded++;
            }
        }

        log.info("AppCache seeded with {} config entries from environment", seeded);
    }

    private record ConfigEntry(String category, String description, boolean isSecret) {}
}

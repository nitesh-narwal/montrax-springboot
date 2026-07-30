package in.tracking.moneymanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration that logs MongoDB status on startup.
 * Shows whether MongoDB is configured or not.
 */
@Configuration
@Slf4j
public class DisableMongoConfig {

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @PostConstruct
    public void logMongoStatus() {
        log.info("======================================");
        if (mongoUri == null || mongoUri.isEmpty()) {
            log.info("MongoDB is NOT configured.");
            log.info("AI query history will not be persisted.");
            log.info("Set MONGODB_URI environment variable to enable MongoDB features.");
        } else {
            log.info("MongoDB is CONFIGURED and connected.");
            log.info("AI query history will be persisted to MongoDB.");
        }
        log.info("======================================");
    }
}



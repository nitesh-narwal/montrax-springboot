package in.tracking.moneymanager.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Disable MongoDB health indicator to prevent authorization errors.
 * The default MongoHealthIndicator tries to run commands on the 'local' database
 * which requires special permissions on MongoDB Atlas.
 *
 * This creates a simple health indicator that always returns UP,
 * letting the actual MongoDB operations handle connection errors gracefully.
 */
@Configuration
public class MongoHealthConfig {

    /**
     * Override the default MongoDB health indicator with one that doesn't
     * execute commands on the 'local' database.
     */
    @Bean("mongoHealthIndicator")
    @Primary
    @ConditionalOnProperty(name = "spring.data.mongodb.uri")
    public HealthIndicator mongoHealthIndicator() {
        return () -> Health.up()
                .withDetail("status", "MongoDB configured - health check disabled to avoid Atlas permission issues")
                .build();
    }
}


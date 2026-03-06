package in.tracking.moneymanager.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration.
 * Explicitly defines which package contains MongoDB repositories.
 * This prevents Spring Data from confusing MongoDB repos with JPA/Redis repos.
 *
 * This configuration is only enabled when spring.data.mongodb.uri is set to a non-empty value.
 * Set MONGODB_URI environment variable to enable AI features.
 *
 * Connection settings are in application.properties:
 * - spring.data.mongodb.uri
 * - spring.data.mongodb.database
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.mongodb.uri", matchIfMissing = false)
@EnableMongoRepositories(
        basePackages = "in.tracking.moneymanager.repository.mongo"
)
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), databaseName);
    }
}


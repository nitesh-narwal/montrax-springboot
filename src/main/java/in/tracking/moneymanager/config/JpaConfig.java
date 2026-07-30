package in.tracking.moneymanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration.
 * Explicitly defines which package contains JPA repositories.
 * This prevents Spring Data from confusing JPA repos with MongoDB/Redis repos.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "in.tracking.moneymanager.repository",
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.REGEX,
                pattern = "in\\.tracking\\.moneymanager\\.repository\\.mongo\\..*"
        )
)
public class JpaConfig {
    // Spring Boot auto-configures JPA from properties
}


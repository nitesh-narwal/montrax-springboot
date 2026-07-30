package in.tracking.moneymanager.service;

import in.tracking.moneymanager.document.AppConfigDocument;
import in.tracking.moneymanager.repository.mongo.AppConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AppCacheService {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private final Environment environment;

    @Autowired(required = false)
    private AppConfigRepository appConfigRepository;

    private volatile boolean mongoAvailable = false;

    public AppCacheService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        loadCache();
        log.info("AppCacheService initialized with {} config entries (MongoDB available: {})", cache.size(), mongoAvailable);
    }

    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void scheduledRefresh() {
        log.info("Scheduled AppCache refresh triggered");
        loadCache();
    }

    public void forceRefresh() {
        log.info("Force AppCache refresh triggered by admin");
        loadCache();
    }

    private void loadCache() {
        if (appConfigRepository == null) {
            mongoAvailable = false;
            log.debug("MongoDB not available, using environment variables only");
            return;
        }

        try {
            List<AppConfigDocument> configs = appConfigRepository.findAll();
            if (configs.isEmpty()) {
                mongoAvailable = true;
                log.debug("No config entries in MongoDB, using environment variables as fallback");
                return;
            }

            ConcurrentHashMap<String, String> newCache = new ConcurrentHashMap<>();
            for (AppConfigDocument config : configs) {
                if (config.getConfigValue() != null) {
                    newCache.put(config.getConfigKey(), config.getConfigValue());
                }
            }

            cache.clear();
            cache.putAll(newCache);
            mongoAvailable = true;
            log.info("AppCache refreshed with {} entries from MongoDB", cache.size());
        } catch (Exception e) {
            mongoAvailable = false;
            log.warn("Failed to load config from MongoDB, using environment variables: {}", e.getMessage());
        }
    }

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, String defaultValue) {
        // Try in-memory cache first (loaded from MongoDB)
        String value = cache.get(key);
        if (value != null) {
            return value;
        }

        // Fall back to Spring Environment (application.properties / env vars)
        String envValue = environment.getProperty(key);
        if (envValue != null) {
            return envValue;
        }

        return defaultValue;
    }

    public void set(String key, String value, String category, String description, boolean isSecret, String updatedBy) {
        if (appConfigRepository == null) {
            throw new RuntimeException("MongoDB is not available. Cannot persist config changes.");
        }

        AppConfigDocument config = appConfigRepository.findByConfigKey(key)
                .orElse(AppConfigDocument.builder()
                        .configKey(key)
                        .category(category)
                        .description(description)
                        .isSecret(isSecret)
                        .build());

        config.setConfigValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(updatedBy);

        if (category != null) config.setCategory(category);
        if (description != null) config.setDescription(description);

        appConfigRepository.save(config);
        cache.put(key, value);

        log.info("Config updated: key={}, category={}, updatedBy={}", key, category, updatedBy);
    }

    public List<AppConfigDocument> getAllByCategory(String category) {
        if (appConfigRepository == null) {
            return List.of();
        }
        return appConfigRepository.findByCategory(category);
    }

    public List<AppConfigDocument> getAll() {
        if (appConfigRepository == null) {
            return List.of();
        }
        return appConfigRepository.findAll();
    }

    public Map<String, String> getAllAsMap() {
        if (!cache.isEmpty()) {
            return Map.copyOf(cache);
        }
        return Map.of();
    }

    public boolean isMongoAvailable() {
        return mongoAvailable;
    }

}

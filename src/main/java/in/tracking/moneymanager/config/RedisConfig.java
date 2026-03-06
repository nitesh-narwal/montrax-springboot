package in.tracking.moneymanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuration for Redis caching.
 * Sets up connection factory, templates, and cache configurations.
 * Uses RedisSerializer.json() (Spring 4.0+ recommended) for JSON serialization.
 *
 * Cache TTLs:
 * - dashboard: 5 minutes (frequently updated)
 * - subscription: 1 hour (checked often but rarely changes)
 * - categories: 24 hours (rarely changes)
 * - ai-insights: 6 hours (expensive to generate)
 * - analytics: 10 minutes (changes with transactions)
 * - predictions: 6 hours (computed from historical data)
 * - anomalies: 30 minutes (recent data)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Create Redis connection factory.
     * Uses Lettuce client for better performance.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);

        // Set password if provided
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        return new LettuceConnectionFactory(config);
    }

    /**
     * Create RedisTemplate for manual Redis operations.
     * Used for rate limiting, session management, etc.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys (readable in Redis CLI)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer for values (Spring 4.0+ recommended approach)
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    /**
     * Configure cache manager with different TTLs for different caches.
     * Each cache has optimized TTL based on data volatility.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Use Spring's recommended JSON serializer
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))  // Default: 30 minutes
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();  // Don't cache nulls

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)

                // Dashboard data - short TTL as it changes frequently
                .withCacheConfiguration("dashboard",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))

                // Subscription status - checked often but rarely changes
                .withCacheConfiguration("subscription",
                        defaultConfig.entryTtl(Duration.ofHours(1)))

                // Categories - rarely changes, long TTL
                .withCacheConfiguration("categories",
                        defaultConfig.entryTtl(Duration.ofHours(24)))

                // AI Insights - expensive to generate, cache longer
                .withCacheConfiguration("ai-insights",
                        defaultConfig.entryTtl(Duration.ofHours(6)))

                // Monthly summary - moderate TTL
                .withCacheConfiguration("monthly-summary",
                        defaultConfig.entryTtl(Duration.ofMinutes(15)))

                // Financial health score - AI computed, cache longer
                .withCacheConfiguration("financial-health",
                        defaultConfig.entryTtl(Duration.ofHours(12)))

                // Analytics - changes with transactions, moderate TTL
                .withCacheConfiguration("analytics",
                        defaultConfig.entryTtl(Duration.ofMinutes(10)))

                // Predictions - computed from historical data, cache longer
                .withCacheConfiguration("predictions",
                        defaultConfig.entryTtl(Duration.ofHours(6)))

                // Anomalies - recent data, shorter TTL
                .withCacheConfiguration("anomalies",
                        defaultConfig.entryTtl(Duration.ofMinutes(30)))

                // AI Tips - expensive Gemini API call, cache for 6 hours
                .withCacheConfiguration("ai-tips",
                        defaultConfig.entryTtl(Duration.ofHours(6)))

                .build();
    }
}


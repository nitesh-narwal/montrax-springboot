package in.tracking.moneymanager.config;

import java.util.Map;

/**
 * Tier-based hourly request limits enforced by RateLimitFilter.
 * ADMIN is exempt entirely (checked before these limits apply).
 */
public class RateLimitConfig {

    public static final Map<String, Integer> HOURLY_LIMITS = Map.of(
            "FREE", 100,
            "BASIC", 500,
            "PREMIUM", 2000
    );

    public static final int DEFAULT_LIMIT = HOURLY_LIMITS.get("FREE");

    private RateLimitConfig() {
    }
}

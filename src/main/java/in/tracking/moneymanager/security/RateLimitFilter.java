package in.tracking.moneymanager.security;

import in.tracking.moneymanager.config.RateLimitConfig;
import in.tracking.moneymanager.repository.ProfileRepository;
import in.tracking.moneymanager.repository.SubscriptionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Per-user, tier-based hourly rate limiting via Redis counters.
 * Runs after JwtRequestFilter so SecurityContext is already populated.
 * ADMIN role is exempt. Anonymous/unauthenticated requests pass through
 * (Spring Security enforces auth separately for protected endpoints).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final String TIER_CACHE_KEY_PREFIX = "tier_cache:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (isAdmin) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = authentication.getName();
        String tier = resolveTier(email);
        int limit = RateLimitConfig.HOURLY_LIMITS.getOrDefault(tier, RateLimitConfig.DEFAULT_LIMIT);

        String bucket = LocalDateTime.now().format(HOUR_BUCKET);
        String counterKey = RATE_LIMIT_KEY_PREFIX + email + ":" + bucket;

        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(counterKey, Duration.ofHours(1));
        }

        if (count != null && count > limit) {
            log.warn("Rate limit exceeded for user: {} (tier: {}, limit: {}/hr)", email, tier, limit);
            response.setStatus(429);
            response.setHeader("Retry-After", "3600");
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\",\"tier\":\"%s\",\"limitPerHour\":%d}", tier, limit));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveTier(String email) {
        String cacheKey = TIER_CACHE_KEY_PREFIX + email;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached.toString();
        }

        String tier = profileRepository.findByEmail(email)
                .map(profile -> subscriptionRepository.findByProfileIdAndStatus(profile.getId(), "ACTIVE")
                        .or(() -> subscriptionRepository.findByProfileIdAndStatus(profile.getId(), "GRACE_PERIOD"))
                        .map(sub -> sub.getPlanType())
                        .orElse("FREE"))
                .orElse("FREE");

        redisTemplate.opsForValue().set(cacheKey, tier, Duration.ofMinutes(10));
        return tier;
    }
}

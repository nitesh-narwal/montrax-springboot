package in.tracking.moneymanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Deduplicates POST/PUT requests carrying an X-Idempotency-Key header.
 * First request executes and its response is cached in Redis for 24h;
 * replays with the same key (per user) return the cached response
 * without re-executing the handler.
 *
 * Requests without the header pass through untouched - idempotency is opt-in per call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Idempotency-Key";
    private static final String KEY_PREFIX = "idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String idempotencyKey = request.getHeader(HEADER);
        boolean applicable = (method.equals("POST") || method.equals("PUT"))
                && idempotencyKey != null && !idempotencyKey.isBlank();

        if (!applicable) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userKey = authentication != null ? authentication.getName() : "anonymous";
        String redisKey = KEY_PREFIX + userKey + ":" + idempotencyKey;

        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("Idempotent replay for user: {}, key: {}", userKey, idempotencyKey);
            response.setStatus((Integer) cached.get("status"));
            response.setContentType((String) cached.get("contentType"));
            response.getWriter().write((String) cached.get("body"));
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);

        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            String contentType = wrappedResponse.getContentType() != null ? wrappedResponse.getContentType() : "application/json";
            redisTemplate.opsForValue().set(redisKey,
                    Map.of("status", status, "contentType", contentType, "body", body),
                    Duration.ofHours(24));
        }
        wrappedResponse.copyBodyToResponse();
    }
}

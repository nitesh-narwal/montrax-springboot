package in.tracking.moneymanager.security;

import in.tracking.moneymanager.dto.event.AuditEvent;
import in.tracking.moneymanager.service.event.AuditEventPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Publishes one AuditEvent per authenticated request to Kafka for the audit trail.
 * Runs last in the security filter chain so status code and timing reflect the
 * actual outcome of request processing.
 */
@Component
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    private final AuditEventPublisher auditEventPublisher;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                auditEventPublisher.publish(AuditEvent.builder()
                        .userEmail(authentication.getName())
                        .method(request.getMethod())
                        .path(request.getRequestURI())
                        .statusCode(response.getStatus())
                        .ipAddress(request.getRemoteAddr())
                        .durationMs(System.currentTimeMillis() - start)
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        }
    }
}

package in.tracking.moneymanager.aspect;

import in.tracking.moneymanager.annotation.AdminOnly;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enforces ROLE_ADMIN on methods annotated with @AdminOnly.
 * Belt-and-braces alongside SecurityConfig's /api/admin/** matcher.
 */
@Aspect
@Component
@Slf4j
public class AdminAccessAspect {

    @Around("@annotation(adminOnly)")
    public Object checkAdminAccess(ProceedingJoinPoint joinPoint, AdminOnly adminOnly) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (!isAdmin) {
            String user = authentication != null ? authentication.getName() : "anonymous";
            log.warn("Admin access denied for user: {} on method: {}", user, joinPoint.getSignature().toShortString());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }

        return joinPoint.proceed();
    }
}

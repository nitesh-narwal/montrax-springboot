package in.tracking.moneymanager.aspect;

import in.tracking.moneymanager.annotation.PremiumFeature;
import in.tracking.moneymanager.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aspect that checks premium feature access before method execution.
 * Works with @PremiumFeature annotation to gate premium features.
 *
 * If user doesn't have required subscription:
 * - Throws 403 Forbidden with helpful message
 * - Logs access attempt for monitoring
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumFeatureAspect {

    private final SubscriptionService subscriptionService;

    /**
     * Intercepts methods annotated with @PremiumFeature.
     * Checks if current user has required subscription before allowing execution.
     *
     * @param joinPoint The method being called
     * @param premiumFeature The annotation with required plans
     * @return Method result if access granted
     * @throws ResponseStatusException 403 if access denied
     */
    @Around("@annotation(premiumFeature)")
    public Object checkPremiumAccess(ProceedingJoinPoint joinPoint,
                                      PremiumFeature premiumFeature) throws Throwable {

        String[] requiredPlans = premiumFeature.requiredPlans();
        String featureName = premiumFeature.featureName();

        // Check if user has access based on their subscription
        if (!subscriptionService.hasAccess(requiredPlans)) {
            log.warn("Premium feature access denied. Feature: {}, Required plans: {}",
                    featureName, String.join(", ", requiredPlans));

            // Build helpful error message
            String message = String.format(
                    "This feature requires a %s subscription. Please upgrade to access: %s",
                    String.join(" or ", requiredPlans),
                    featureName.isEmpty() ? "this feature" : featureName
            );

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }

        // User has access - proceed with method execution
        log.debug("Premium feature access granted for: {}", featureName);
        return joinPoint.proceed();
    }
}


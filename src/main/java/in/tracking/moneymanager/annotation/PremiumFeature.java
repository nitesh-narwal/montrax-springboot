package in.tracking.moneymanager.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that require premium subscription.
 * Used with PremiumFeatureAspect to check access before method execution.
 *
 * Usage example:
 * @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Analysis")
 * public ResponseEntity<?> analyzeSpending() { ... }
 *
 * If user doesn't have required plan, returns 403 Forbidden.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PremiumFeature {

    // Plans that can access this feature (default: BASIC and PREMIUM)
    String[] requiredPlans() default {"BASIC", "PREMIUM"};

    // Feature name for error messages (shown to user when access denied)
    String featureName() default "";
}


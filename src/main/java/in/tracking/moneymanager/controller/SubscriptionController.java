package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.SubscriptionDTO;
import in.tracking.moneymanager.dto.SubscriptionPlanDTO;
import in.tracking.moneymanager.service.DataRetentionService;
import in.tracking.moneymanager.service.ProfileService;
import in.tracking.moneymanager.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for subscription management.
 * Handles plan listing, subscription status, and cancellation.
 *
 * Endpoints:
 * - GET /api/subscription/plans - List all available plans (public)
 * - GET /api/subscription/current - Get current subscription (authenticated)
 * - POST /api/subscription/cancel - Cancel subscription (authenticated)
 * - GET /api/subscription/data-retention - Get data retention info (authenticated)
 */
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final DataRetentionService dataRetentionService;
    private final ProfileService profileService;

    /**
     * Get all available subscription plans.
     * Public endpoint - no authentication required.
     * Used to display pricing page.
     *
     * @return List of available plans (FREE, BASIC, PREMIUM)
     */
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(subscriptionService.getAllPlans());
    }

    /**
     * Get current user's subscription status.
     * Includes plan details, validity, and usage statistics.
     *
     * @return Current subscription details
     */
    @GetMapping("/current")
    public ResponseEntity<SubscriptionDTO> getCurrentSubscription() {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription());
    }

    /**
     * Cancel current subscription.
     * Subscription remains active until end date but won't auto-renew.
     *
     * @param subscriptionId ID of subscription to cancel
     * @return Success response
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @RequestParam Long subscriptionId) {

        subscriptionService.cancelSubscription(subscriptionId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription cancelled. You can continue using premium features until the end date."
        ));
    }

    /**
     * Get data retention information for current user.
     * Shows how long data is kept based on subscription plan.
     *
     * @return Data retention details
     */
    @GetMapping("/data-retention")
    public ResponseEntity<Map<String, Object>> getDataRetentionInfo() {
        Long profileId = profileService.getCurrentProfile().getId();
        DataRetentionService.DataRetentionInfo info = dataRetentionService.getRetentionInfo(profileId);

        return ResponseEntity.ok(Map.of(
                "retentionMonths", info.retentionMonths(),
                "isUnlimited", info.isUnlimited(),
                "nextCleanupCutoffDate", info.nextCleanupCutoffDate() != null ? info.nextCleanupCutoffDate().toString() : null,
                "recordsToBeDeleted", info.recordsToBeDeleted(),
                "message", info.isUnlimited()
                        ? "Your data is retained indefinitely with your current plan."
                        : String.format("Data older than %d months will be automatically deleted.", info.retentionMonths())
        ));
    }
}


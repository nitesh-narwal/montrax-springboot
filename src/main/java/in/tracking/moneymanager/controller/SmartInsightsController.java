package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.annotation.PremiumFeature;
import in.tracking.moneymanager.service.SmartInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for smart financial insights.
 * Provides predictions, anomaly detection, and AI-powered tips.
 *
 * All endpoints require BASIC or PREMIUM subscription.
 *
 * Endpoints:
 * - GET /api/insights/predictions - Get spending predictions
 * - GET /api/insights/anomalies - Get spending anomalies
 * - GET /api/insights/tips - Get AI-generated tips (uses Gemini)
 * - GET /api/insights/summary - Get spending summary (no AI)
 */
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class SmartInsightsController {

    private final SmartInsightsService smartInsightsService;

    /**
     * Get next month's spending prediction.
     * Uses weighted moving average of last 3 months (no ML).
     *
     * Returns:
     * - Predicted total for next month
     * - Predictions per category
     * - Confidence level (HIGH, MEDIUM, LOW)
     */
    @GetMapping("/predictions")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Spending Predictions")
    public ResponseEntity<Map<String, Object>> getPredictions() {
        return ResponseEntity.ok(smartInsightsService.predictNextMonthSpending());
    }

    /**
     * Get detected spending anomalies from last 7 days.
     * Flags transactions > 2 standard deviations from category mean.
     *
     * Returns list of anomalies with:
     * - Transaction details
     * - Category average
     * - Percentage above average
     * - Severity (HIGH or MEDIUM)
     */
    @GetMapping("/anomalies")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Anomaly Detection")
    public ResponseEntity<List<Map<String, Object>>> getAnomalies() {
        return ResponseEntity.ok(smartInsightsService.detectAnomalies());
    }

    /**
     * Get personalized money-saving tips using Gemini AI.
     * WARNING: This counts towards monthly AI query limit.
     *
     * Returns:
     * - 3 specific tips based on spending patterns
     * - Top spending category
     * - Generation timestamp
     */
    @GetMapping("/tips")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Tips")
    public ResponseEntity<Map<String, Object>> getTips() {
        return ResponseEntity.ok(smartInsightsService.getPersonalizedTips());
    }

    /**
     * Get spending summary with basic insights.
     * Lighter alternative to AI tips - no Gemini API call.
     *
     * Returns:
     * - Current month spending
     * - Comparison with last month
     * - Daily average
     * - Projected month total
     * - Trend (UP, DOWN, SAME)
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(smartInsightsService.getSpendingSummary());
    }
}


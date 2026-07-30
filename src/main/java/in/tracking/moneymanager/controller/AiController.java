package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.annotation.PremiumFeature;
import in.tracking.moneymanager.document.AiInsightDocument;
import in.tracking.moneymanager.document.AiQueryHistoryDocument;
import in.tracking.moneymanager.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for AI-powered financial analysis features.
 * Most endpoints require BASIC or PREMIUM subscription.
 *
 * Endpoints:
 * - POST /api/ai/analyze-spending - Analyze spending patterns [BASIC+]
 * - POST /api/ai/savings-strategy - Get savings strategy [PREMIUM]
 * - GET /api/ai/financial-health - Get health score [BASIC+]
 * - POST /api/ai/ask - Ask financial question [BASIC+]
 * - GET /api/ai/insights - Get stored insights [BASIC+]
 * - GET /api/ai/query-history - Get query history
 * - GET /api/ai/remaining-queries - Get remaining queries
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService aiAnalysisService;

    /**
     * Analyze spending patterns using AI.
     * Returns spending breakdown, unusual patterns, and recommendations.
     *
     * @return Spending analysis with AI insights
     */
    @PostMapping("/analyze-spending")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Spending Analysis")
    public ResponseEntity<Map<String, Object>> analyzeSpending() {
        return ResponseEntity.ok(aiAnalysisService.analyzeSpending());
    }

    /**
     * Get personalized savings strategy from AI.
     * PREMIUM only - provides detailed savings plan.
     *
     * @return Personalized savings strategy
     */
    @PostMapping("/savings-strategy")
    @PremiumFeature(requiredPlans = {"PREMIUM"}, featureName = "AI Savings Strategy")
    public ResponseEntity<Map<String, Object>> getSavingsStrategy() {
        return ResponseEntity.ok(aiAnalysisService.getSavingsStrategy());
    }

    /**
     * Get financial health score (0-100) with breakdown.
     * Results are cached for 24 hours to save AI calls.
     * Use refresh=true to force a new AI analysis.
     *
     * @param refresh If true, bypasses cache and calls AI
     * @return Financial health assessment
     */
    @GetMapping("/financial-health")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Financial Health Score")
    public ResponseEntity<Map<String, Object>> getFinancialHealth(
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {

        Map<String, Object> data = refresh
                ? aiAnalysisService.getFinancialHealth(true)   // force fresh AI call
                : aiAnalysisService.getFinancialHealth();      // cached/default path

        return ResponseEntity.ok(data);
    }

    /**
     * Ask a financial question to AI.
     * User can ask anything related to personal finance.
     *
     * @param request Map containing "question" key
     * @return AI's answer to the question
     */
    @PostMapping("/ask")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Financial Q&A")
    public ResponseEntity<Map<String, Object>> askQuestion(@RequestBody Map<String, String> request) {
        String question = request.get("question");

        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Question is required",
                    "message", "Please provide a question in the 'question' field"
            ));
        }

        return ResponseEntity.ok(aiAnalysisService.askQuestion(question));
    }

    /**
     * Get stored AI insights for the user.
     * Insights are generated from previous analyses.
     *
     * @return List of AI insights
     */
    @GetMapping("/insights")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Insights")
    public ResponseEntity<?> getInsights(
            @RequestParam(required = false) String priority,
            @RequestParam(required = false, name = "type") String insightType) {
        try {
            return ResponseEntity.ok(aiAnalysisService.getInsights(priority, insightType));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

    /**
     * Get AI query history for the user.
     * Shows past 20 queries and responses.
     *
     * @return List of recent AI queries
     */
    @GetMapping("/query-history")
    public ResponseEntity<List<AiQueryHistoryDocument>> getQueryHistory() {
        return ResponseEntity.ok(aiAnalysisService.getQueryHistory());
    }

    /**
     * Get remaining AI queries for current month.
     * Shows used, limit, and remaining counts.
     *
     * @return Query usage statistics
     */
    @GetMapping("/remaining-queries")
    public ResponseEntity<Map<String, Integer>> getRemainingQueries() {
        return ResponseEntity.ok(aiAnalysisService.getRemainingQueries());
    }

    /**
     * Get count of unread AI insights.
     * Used for notification badge.
     *
     * @return Count of unread insights
     */
    @GetMapping("/insights/unread-count")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Insights")
    public ResponseEntity<Map<String, Object>> getUnreadInsightsCount() {
        return ResponseEntity.ok(aiAnalysisService.getUnreadInsightsCount());
    }

    /**
     * Mark an insight as read.
     *
     * @param insightId ID of the insight
     * @return Updated insight
     */
    @PostMapping("/insights/{insightId}/read")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Insights")
    public ResponseEntity<Map<String, Object>> markInsightAsRead(@PathVariable String insightId) {
        return ResponseEntity.ok(aiAnalysisService.markInsightAsRead(insightId));
    }

    /**
     * Mark an insight as action taken (user followed the recommendation).
     *
     * @param insightId ID of the insight
     * @return Updated insight
     */
    @PostMapping("/insights/{insightId}/action-taken")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Insights")
    public ResponseEntity<Map<String, Object>> markInsightActionTaken(@PathVariable String insightId) {
        return ResponseEntity.ok(aiAnalysisService.markInsightActionTaken(insightId));
    }

    @GetMapping("/insights/recent")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Insights")
    public ResponseEntity<List<AiInsightDocument>> getRecentInsights(
            @RequestParam(name = "type") String insightType,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(aiAnalysisService.getRecentInsightsByType(insightType, hours));
    }

    @PostMapping("/insights/cleanup")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "AI Insights")
    public ResponseEntity<Map<String, Object>> cleanupInsights(
            @RequestParam(name = "type") String insightType,
            @RequestParam(defaultValue = "90") int retentionDays) {
        return ResponseEntity.ok(aiAnalysisService.cleanupOldInsightsByType(insightType, retentionDays));
    }
}


package in.tracking.moneymanager.service;

import in.tracking.moneymanager.document.AiInsightDocument;
import in.tracking.moneymanager.document.AiQueryHistoryDocument;
import in.tracking.moneymanager.dto.SubscriptionDTO;
import in.tracking.moneymanager.repository.mongo.AiInsightRepository;
import in.tracking.moneymanager.repository.mongo.AiQueryHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Service for AI-powered financial analysis.
 * Provides spending analysis, savings strategies, financial health scoring,
 * and natural language Q&A.
 *
 * Features:
 * - Spending pattern analysis
 * - Personalized savings strategy
 * - Financial health score (0-100)
 * - Natural language financial questions
 *
 * Rate limits based on subscription:
 * - FREE: 0 queries/month
 * - BASIC: 5 queries/month
 * - PREMIUM: 50 queries/month
 *
 * Note: MongoDB repositories are optional. If MongoDB is not configured,
 * query history won't be persisted but AI features will still work.
 */
@Service
@Slf4j
public class AiAnalysisService {

    private final GeminiService geminiService;
    private final ProfileService profileService;
    private final SubscriptionService subscriptionService;
    private final DashboardService dashboardService;

    // Optional MongoDB repositories - null if MongoDB is not configured
    private final AiInsightRepository aiInsightRepository;
    private final AiQueryHistoryRepository aiQueryHistoryRepository;

    @Autowired
    public AiAnalysisService(
            GeminiService geminiService,
            ProfileService profileService,
            SubscriptionService subscriptionService,
            DashboardService dashboardService,
            @Autowired(required = false) AiInsightRepository aiInsightRepository,
            @Autowired(required = false) AiQueryHistoryRepository aiQueryHistoryRepository) {
        this.geminiService = geminiService;
        this.profileService = profileService;
        this.subscriptionService = subscriptionService;
        this.dashboardService = dashboardService;
        this.aiInsightRepository = aiInsightRepository;
        this.aiQueryHistoryRepository = aiQueryHistoryRepository;

        if (aiInsightRepository == null || aiQueryHistoryRepository == null) {
            log.warn("MongoDB not configured - AI query history will not be persisted");
        }
    }

    /**
     * Analyze user's spending patterns and provide insights.
     * Returns spending breakdown, unusual patterns, and recommendations.
     *
     * @return Map containing analysis results
     */
    public Map<String, Object> analyzeSpending() {
        // Check if user has remaining AI queries
        checkAndRecordQuery("ANALYZE");

        Long profileId = profileService.getCurrentProfile().getId();

        // Gather financial data from dashboard service
        Map<String, Object> dashboardData = dashboardService.getDashboardData();
        BigDecimal totalIncome = (BigDecimal) dashboardData.getOrDefault("totalIncome", BigDecimal.ZERO);
        BigDecimal totalExpense = (BigDecimal) dashboardData.getOrDefault("totalExpense", BigDecimal.ZERO);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> categoryTotals = (Map<String, BigDecimal>) dashboardData.getOrDefault("categoryBreakdown", new HashMap<>());

        // Build AI prompt
        String prompt = buildSpendingAnalysisPrompt(totalIncome, totalExpense, categoryTotals);

        // Get AI analysis
        long startTime = System.currentTimeMillis();
        String aiResponse = geminiService.generateResponse(prompt);
        long responseTime = System.currentTimeMillis() - startTime;

        // Save query history
        saveQueryHistory(profileId, "ANALYZE", null, aiResponse, responseTime);

        // Calculate potential savings (10% of expenses as estimate)
        BigDecimal potentialSavings = totalExpense.multiply(BigDecimal.valueOf(0.10));

        // Determine priority based on savings rate
        BigDecimal savingsRate = calculateSavingsRate(totalIncome, totalExpense);
        String priority = savingsRate.compareTo(BigDecimal.valueOf(10)) < 0 ? "HIGH" :
                         savingsRate.compareTo(BigDecimal.valueOf(20)) < 0 ? "MEDIUM" : "LOW";

        // Save insight to MongoDB for later retrieval
        Map<String, Object> insightData = new HashMap<>();
        insightData.put("totalIncome", totalIncome);
        insightData.put("totalExpense", totalExpense);
        insightData.put("savingsRate", savingsRate);
        insightData.put("categoryBreakdown", categoryTotals);

        saveInsight(profileId, "SPENDING_ANALYSIS",
                "Spending Analysis - " + LocalDateTime.now().toLocalDate(),
                aiResponse, insightData, priority, potentialSavings);

        // Build response
        Map<String, Object> result = new HashMap<>();
        result.put("analysis", aiResponse);
        result.put("totalIncome", totalIncome);
        result.put("totalExpense", totalExpense);
        result.put("categoryBreakdown", categoryTotals);
        result.put("savingsRate", savingsRate);
        result.put("potentialSavings", potentialSavings);
        result.put("priority", priority);
        result.put("analyzedAt", LocalDateTime.now());

        return result;
    }

    /**
     * Generate personalized savings strategy based on user's finances.
     *
     * @return Map containing savings strategy
     */
    public Map<String, Object> getSavingsStrategy() {
        checkAndRecordQuery("STRATEGY");

        Long profileId = profileService.getCurrentProfile().getId();

        Map<String, Object> dashboardData = dashboardService.getDashboardData();
        BigDecimal totalIncome = (BigDecimal) dashboardData.getOrDefault("totalIncome", BigDecimal.ZERO);
        BigDecimal totalExpense = (BigDecimal) dashboardData.getOrDefault("totalExpense", BigDecimal.ZERO);
        BigDecimal savings = totalIncome.subtract(totalExpense);

        String prompt = buildSavingsStrategyPrompt(totalIncome, totalExpense, savings);

        long startTime = System.currentTimeMillis();
        String aiResponse = geminiService.generateResponse(prompt);
        long responseTime = System.currentTimeMillis() - startTime;

        saveQueryHistory(profileId, "STRATEGY", null, aiResponse, responseTime);

        // Calculate savings rate and determine priority
        BigDecimal savingsRate = calculateSavingsRate(totalIncome, totalExpense);
        String priority = savingsRate.compareTo(BigDecimal.valueOf(10)) < 0 ? "HIGH" :
                         savingsRate.compareTo(BigDecimal.valueOf(20)) < 0 ? "MEDIUM" : "LOW";

        // Calculate target savings (recommended 20% of income)
        BigDecimal targetSavings = totalIncome.multiply(BigDecimal.valueOf(0.20));
        BigDecimal potentialSavings = targetSavings.subtract(savings).max(BigDecimal.ZERO);

        // Save insight to MongoDB
        Map<String, Object> insightData = new HashMap<>();
        insightData.put("currentSavings", savings);
        insightData.put("targetSavings", targetSavings);
        insightData.put("savingsRate", savingsRate);
        insightData.put("totalIncome", totalIncome);
        insightData.put("totalExpense", totalExpense);

        saveInsight(profileId, "SAVINGS_STRATEGY",
                "Savings Strategy - " + LocalDateTime.now().toLocalDate(),
                aiResponse, insightData, priority, potentialSavings);

        Map<String, Object> result = new HashMap<>();
        result.put("strategy", aiResponse);
        result.put("currentSavings", savings);
        result.put("targetSavings", targetSavings);
        result.put("savingsRate", savingsRate);
        result.put("potentialSavings", potentialSavings);
        result.put("priority", priority);
        result.put("generatedAt", LocalDateTime.now());

        return result;
    }

    /**
     * Calculate financial health score (0-100).
     * Uses caching to avoid repeated AI calls - refreshes if:
     * 1. No cached data exists
     * 2. Cache is older than 24 hours
     * 3. Income/expense totals have changed
     * 4. forceRefresh parameter is true
     *
     * @return Map containing health score and breakdown
     */
    public Map<String, Object> getFinancialHealth() {
        return getFinancialHealth(false);
    }

    /**
     * Calculate financial health score with optional force refresh.
     *
     * @param forceRefresh If true, bypasses cache and calls AI
     * @return Map containing health score and breakdown
     */
    public Map<String, Object> getFinancialHealth(boolean forceRefresh) {
        Long profileId = profileService.getCurrentProfile().getId();

        Map<String, Object> dashboardData = dashboardService.getDashboardData();
        BigDecimal totalIncome = (BigDecimal) dashboardData.getOrDefault("totalIncome", BigDecimal.ZERO);
        BigDecimal totalExpense = (BigDecimal) dashboardData.getOrDefault("totalExpense", BigDecimal.ZERO);

        // Try to get cached health data if not forcing refresh
        if (!forceRefresh && aiInsightRepository != null) {
            try {
                Optional<AiInsightDocument> cachedInsight = aiInsightRepository
                        .findTopByProfileIdAndInsightTypeOrderByCreatedAtDesc(profileId, "FINANCIAL_HEALTH");

                if (cachedInsight.isPresent()) {
                    AiInsightDocument cached = cachedInsight.get();
                    Map<String, Object> cachedData = cached.getData();

                    // Check if cache is still valid (less than 24 hours old)
                    boolean cacheValid = cached.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24));

                    // Check if financial data has changed
                    BigDecimal cachedIncome = toBigDecimal(cachedData.get("totalIncome"));
                    BigDecimal cachedExpense = toBigDecimal(cachedData.get("totalExpense"));
                    boolean dataUnchanged = cachedIncome.compareTo(totalIncome) == 0
                            && cachedExpense.compareTo(totalExpense) == 0;

                    if (cacheValid && dataUnchanged) {
                        log.info("Returning cached financial health for profile {} (cached at {})",
                                profileId, cached.getCreatedAt());

                        Map<String, Object> result = new HashMap<>();
                        result.put("analysis", cached.getDescription());
                        result.put("healthScore", cachedData.get("healthScore"));
                        result.put("healthStatus", cachedData.get("healthStatus"));
                        result.put("savingsRate", toBigDecimal(cachedData.get("savingsRate")));
                        result.put("priority", cached.getPriority());
                        result.put("calculatedAt", cached.getCreatedAt());
                        result.put("cached", true);
                        return result;
                    } else {
                        log.info("Cache invalid for profile {} - cacheValid={}, dataUnchanged={}",
                                profileId, cacheValid, dataUnchanged);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read cached financial health from MongoDB, will compute fresh: {}", e.getMessage());
                // Continue to compute fresh health score
            }
        }

        // Cache miss or invalid - call AI
        checkAndRecordQuery("HEALTH");

        String prompt = buildHealthScorePrompt(totalIncome, totalExpense);

        long startTime = System.currentTimeMillis();
        String aiResponse = geminiService.generateResponse(prompt);
        long responseTime = System.currentTimeMillis() - startTime;

        saveQueryHistory(profileId, "HEALTH", null, aiResponse, responseTime);

        // Calculate health score based on savings rate
        BigDecimal savingsRate = calculateSavingsRate(totalIncome, totalExpense);
        int healthScore = calculateHealthScore(savingsRate);
        String healthStatus = getHealthStatus(healthScore);
        String priority = healthScore < 40 ? "HIGH" : healthScore < 60 ? "MEDIUM" : "LOW";

        // Save insight to MongoDB (this serves as our cache)
        Map<String, Object> insightData = new HashMap<>();
        insightData.put("healthScore", healthScore);
        insightData.put("healthStatus", healthStatus);
        insightData.put("savingsRate", savingsRate);
        insightData.put("totalIncome", totalIncome);
        insightData.put("totalExpense", totalExpense);

        saveInsight(profileId, "FINANCIAL_HEALTH",
                "Financial Health Score - " + LocalDateTime.now().toLocalDate(),
                aiResponse, insightData, priority, null);

        Map<String, Object> result = new HashMap<>();
        result.put("analysis", aiResponse);
        result.put("healthScore", healthScore);
        result.put("healthStatus", healthStatus);
        result.put("savingsRate", savingsRate);
        result.put("priority", priority);
        result.put("calculatedAt", LocalDateTime.now());
        result.put("cached", false);

        return result;
    }

    /**
     * Helper to safely convert Object to BigDecimal.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Calculate health score (0-100) based on savings rate.
     */
    private int calculateHealthScore(BigDecimal savingsRate) {
        // Score: 0% savings = 20, 10% = 50, 20% = 70, 30%+ = 90+
        if (savingsRate.compareTo(BigDecimal.ZERO) <= 0) return 20;
        if (savingsRate.compareTo(BigDecimal.valueOf(5)) < 0) return 30;
        if (savingsRate.compareTo(BigDecimal.valueOf(10)) < 0) return 45;
        if (savingsRate.compareTo(BigDecimal.valueOf(15)) < 0) return 55;
        if (savingsRate.compareTo(BigDecimal.valueOf(20)) < 0) return 65;
        if (savingsRate.compareTo(BigDecimal.valueOf(25)) < 0) return 75;
        if (savingsRate.compareTo(BigDecimal.valueOf(30)) < 0) return 85;
        return 95;
    }

    /**
     * Get health status label based on score.
     */
    private String getHealthStatus(int score) {
        if (score >= 80) return "EXCELLENT";
        if (score >= 60) return "GOOD";
        if (score >= 40) return "FAIR";
        if (score >= 20) return "POOR";
        return "CRITICAL";
    }

    /**
     * Answer a user's financial question using AI.
     *
     * @param question User's question in natural language
     * @return Map containing question and answer
     */
    public Map<String, Object> askQuestion(String question) {
        checkAndRecordQuery("ASK");

        Long profileId = profileService.getCurrentProfile().getId();

        Map<String, Object> dashboardData = dashboardService.getDashboardData();
        BigDecimal totalIncome = (BigDecimal) dashboardData.getOrDefault("totalIncome", BigDecimal.ZERO);
        BigDecimal totalExpense = (BigDecimal) dashboardData.getOrDefault("totalExpense", BigDecimal.ZERO);

        String prompt = buildQuestionPrompt(question, totalIncome, totalExpense);

        long startTime = System.currentTimeMillis();
        String aiResponse = geminiService.generateResponse(prompt);
        long responseTime = System.currentTimeMillis() - startTime;

        saveQueryHistory(profileId, "ASK", question, aiResponse, responseTime);

        Map<String, Object> result = new HashMap<>();
        result.put("question", question);
        result.put("answer", aiResponse);
        result.put("answeredAt", LocalDateTime.now());

        return result;
    }

    /**
     * Get stored AI insights for the user.
     *
     * @return List of AI insights
     */
    public List<AiInsightDocument> getInsights() {
        if (aiInsightRepository == null) {
            log.warn("MongoDB not configured - cannot retrieve AI insights");
            return Collections.emptyList();
        }
        Long profileId = profileService.getCurrentProfile().getId();
        return aiInsightRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
    }

    /**
     * Get count of unread AI insights for notification badge.
     *
     * @return Map containing unread count
     */
    public Map<String, Object> getUnreadInsightsCount() {
        if (aiInsightRepository == null) {
            return Map.of("unreadCount", 0, "mongoConfigured", false);
        }
        Long profileId = profileService.getCurrentProfile().getId();
        List<AiInsightDocument> unread = aiInsightRepository.findByProfileIdAndIsReadFalse(profileId);
        return Map.of(
                "unreadCount", unread.size(),
                "mongoConfigured", true
        );
    }

    /**
     * Mark an insight as read.
     *
     * @param insightId ID of the insight
     * @return Map with success status
     */
    public Map<String, Object> markInsightAsRead(String insightId) {
        if (aiInsightRepository == null) {
            return Map.of("success", false, "message", "MongoDB not configured");
        }

        return aiInsightRepository.findById(insightId)
                .map(insight -> {
                    Long profileId = profileService.getCurrentProfile().getId();
                    if (!insight.getProfileId().equals(profileId)) {
                        return Map.<String, Object>of("success", false, "message", "Access denied");
                    }
                    insight.setIsRead(true);
                    aiInsightRepository.save(insight);
                    return Map.<String, Object>of("success", true, "message", "Insight marked as read");
                })
                .orElse(Map.of("success", false, "message", "Insight not found"));
    }

    /**
     * Mark an insight as action taken (user followed the recommendation).
     *
     * @param insightId ID of the insight
     * @return Map with success status
     */
    public Map<String, Object> markInsightActionTaken(String insightId) {
        if (aiInsightRepository == null) {
            return Map.of("success", false, "message", "MongoDB not configured");
        }

        return aiInsightRepository.findById(insightId)
                .map(insight -> {
                    Long profileId = profileService.getCurrentProfile().getId();
                    if (!insight.getProfileId().equals(profileId)) {
                        return Map.<String, Object>of("success", false, "message", "Access denied");
                    }
                    insight.setIsActionTaken(true);
                    insight.setIsRead(true);  // Also mark as read
                    aiInsightRepository.save(insight);
                    return Map.<String, Object>of("success", true, "message", "Insight marked as action taken");
                })
                .orElse(Map.of("success", false, "message", "Insight not found"));
    }

    /**
     * Get AI query history for the user.
     *
     * @return List of recent AI queries
     */
    public List<AiQueryHistoryDocument> getQueryHistory() {
        if (aiQueryHistoryRepository == null) {
            log.warn("MongoDB not configured - cannot retrieve AI query history");
            return Collections.emptyList();
        }
        Long profileId = profileService.getCurrentProfile().getId();
        return aiQueryHistoryRepository.findByProfileIdOrderByCreatedAtDesc(
                profileId,
                PageRequest.of(0, 20)
        );
    }

    /**
     * Get remaining AI queries for current month.
     *
     * @return Map with used and limit counts
     */
    public Map<String, Integer> getRemainingQueries() {
        Long profileId = profileService.getCurrentProfile().getId();
        SubscriptionDTO subscription = subscriptionService.getCurrentSubscription();

        Long used = 0L;
        if (aiQueryHistoryRepository != null) {
            LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
            used = aiQueryHistoryRepository.countByProfileIdAndCreatedAtAfter(profileId, monthStart);
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("used", used.intValue());
        result.put("limit", subscription.getAiQueriesLimit());
        result.put("remaining", Math.max(0, subscription.getAiQueriesLimit() - used.intValue()));

        return result;
    }

    // ==================== Helper Methods ====================

    /**
     * Check if user has remaining AI queries and throw if limit exceeded.
     */
    private void checkAndRecordQuery(String queryType) {
        Long profileId = profileService.getCurrentProfile().getId();
        SubscriptionDTO subscription = subscriptionService.getCurrentSubscription();

        // Check limit based on plan
        int limit = subscription.getAiQueriesLimit();
        if (limit == 0) {
            throw new RuntimeException("AI features require BASIC or PREMIUM subscription. " +
                    "Please upgrade to access AI-powered insights.");
        }

        // Count queries this month (if MongoDB is available)
        Long queriesThisMonth = 0L;
        if (aiQueryHistoryRepository != null) {
            LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
            queriesThisMonth = aiQueryHistoryRepository
                    .countByProfileIdAndCreatedAtAfter(profileId, monthStart);

            if (queriesThisMonth >= limit) {
                throw new RuntimeException(String.format(
                        "AI query limit reached for this month (%d/%d). " +
                        "Please upgrade to PREMIUM for more queries or wait until next month.",
                        queriesThisMonth, limit));
            }
        }

        log.info("User {} making {} AI query. Used: {}/{}",
                profileId, queryType, queriesThisMonth + 1, limit);
    }

    /**
     * Save query to history for tracking and rate limiting.
     */
    private void saveQueryHistory(Long profileId, String queryType, String userQuery,
                                   String aiResponse, long responseTimeMs) {
        if (aiQueryHistoryRepository == null) {
            log.debug("MongoDB not configured - skipping AI query history save");
            return;
        }

        try {
            AiQueryHistoryDocument history = AiQueryHistoryDocument.builder()
                    .profileId(profileId)
                    .queryType(queryType)
                    .userQuery(userQuery)
                    .aiResponse(aiResponse)
                    .responseTimeMs(responseTimeMs)
                    .model("gemini-1.5-flash")
                    .createdAt(LocalDateTime.now())
                    .build();

            aiQueryHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to save AI query history to MongoDB (will continue without persistence): {}", e.getMessage());
            // Don't throw - MongoDB errors shouldn't break AI features
        }
    }

    /**
     * Save AI-generated insight to MongoDB for later retrieval.
     * Insights are stored with title, description, and structured data.
     *
     * @param profileId User's profile ID
     * @param insightType Type of insight (SPENDING_ANALYSIS, SAVINGS_STRATEGY, etc.)
     * @param title Human-readable title
     * @param description AI-generated description/analysis
     * @param data Additional structured data
     * @param priority Priority level (HIGH, MEDIUM, LOW)
     * @param potentialSavings Estimated savings amount
     */
    private void saveInsight(Long profileId, String insightType, String title,
                              String description, Map<String, Object> data,
                              String priority, BigDecimal potentialSavings) {
        if (aiInsightRepository == null) {
            log.debug("MongoDB not configured - skipping AI insight save");
            return;
        }

        try {
            AiInsightDocument insight = AiInsightDocument.builder()
                    .profileId(profileId)
                    .insightType(insightType)
                    .title(title)
                    .description(description)
                    .data(data)
                    .priority(priority)
                    .potentialSavings(potentialSavings)
                    .isRead(false)
                    .isActionTaken(false)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(7))  // Auto-expire after 7 days
                    .build();

            aiInsightRepository.save(insight);
            log.info("Saved AI insight: {} for profile {}", insightType, profileId);
        } catch (Exception e) {
            log.warn("Failed to save AI insight to MongoDB (will continue without persistence): {}", e.getMessage());
            // Don't throw - MongoDB errors shouldn't break AI features
        }
    }

    /**
     * Calculate savings rate as percentage.
     */
    private BigDecimal calculateSavingsRate(BigDecimal income, BigDecimal expense) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal savings = income.subtract(expense);
        return savings.multiply(BigDecimal.valueOf(100))
                .divide(income, 2, RoundingMode.HALF_UP);
    }

    // ==================== Prompt Builders ====================

    private String buildSpendingAnalysisPrompt(BigDecimal income, BigDecimal expense,
                                                Map<String, BigDecimal> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional financial advisor in India. Analyze this spending data:\n\n");
        sb.append("Monthly Income: ₹").append(income).append("\n");
        sb.append("Total Expenses: ₹").append(expense).append("\n");
        sb.append("Savings: ₹").append(income.subtract(expense)).append("\n\n");

        if (!categories.isEmpty()) {
            sb.append("Expenses by Category:\n");
            categories.forEach((cat, amt) ->
                sb.append("- ").append(cat).append(": ₹").append(amt).append("\n"));
            sb.append("\n");
        }

        sb.append("Please provide:\n");
        sb.append("1. A brief summary of spending patterns (2-3 sentences)\n");
        sb.append("2. Top 3 categories where most money is spent\n");
        sb.append("3. Any concerning patterns or overspending areas\n");
        sb.append("4. Estimated potential monthly savings in INR\n");
        sb.append("5. Three specific, actionable recommendations\n\n");
        sb.append("Be specific with amounts in ₹ (INR). Be encouraging but honest. ");
        sb.append("Keep the response concise and practical.");

        return sb.toString();
    }

    private String buildSavingsStrategyPrompt(BigDecimal income, BigDecimal expense,
                                               BigDecimal savings) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a financial planning expert in India. Create a savings strategy:\n\n");
        sb.append("Monthly Income: ₹").append(income).append("\n");
        sb.append("Monthly Expenses: ₹").append(expense).append("\n");
        sb.append("Current Monthly Savings: ₹").append(savings).append("\n\n");

        sb.append("Please provide a detailed savings strategy including:\n");
        sb.append("1. Assessment of current savings rate\n");
        sb.append("2. Recommended monthly savings target (in ₹)\n");
        sb.append("3. Specific expenses to reduce with amounts\n");
        sb.append("4. Step-by-step action plan for next 3 months\n");
        sb.append("5. Emergency fund recommendation\n");
        sb.append("6. Long-term financial goals to work towards\n\n");
        sb.append("Use ₹ (INR) for all amounts. Be practical and achievable.");

        return sb.toString();
    }

    private String buildHealthScorePrompt(BigDecimal income, BigDecimal expense) {
        BigDecimal savings = income.subtract(expense);
        BigDecimal savingsRate = calculateSavingsRate(income, expense);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a financial health assessor in India. Evaluate this situation:\n\n");
        sb.append("Monthly Income: ₹").append(income).append("\n");
        sb.append("Monthly Expenses: ₹").append(expense).append("\n");
        sb.append("Monthly Savings: ₹").append(savings).append("\n");
        sb.append("Savings Rate: ").append(savingsRate).append("%\n\n");

        sb.append("Please provide:\n");
        sb.append("1. Overall financial health score (0-100)\n");
        sb.append("2. Health status: EXCELLENT (80+), GOOD (60-79), FAIR (40-59), POOR (20-39), CRITICAL (<20)\n");
        sb.append("3. Breakdown scores (1-10) for:\n");
        sb.append("   - Savings Rate\n");
        sb.append("   - Expense Management\n");
        sb.append("   - Financial Discipline\n");
        sb.append("4. Top 3 areas for improvement\n");
        sb.append("5. What's working well\n\n");
        sb.append("Be constructive and encouraging. Use Indian context.");

        return sb.toString();
    }

    private String buildQuestionPrompt(String question, BigDecimal income, BigDecimal expense) {
        BigDecimal savings = income.subtract(expense);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful financial advisor in India. Answer this question:\n\n");
        sb.append("User's Question: ").append(question).append("\n\n");
        sb.append("User's Financial Context:\n");
        sb.append("- Monthly Income: ₹").append(income).append("\n");
        sb.append("- Monthly Expenses: ₹").append(expense).append("\n");
        sb.append("- Monthly Savings: ₹").append(savings).append("\n\n");
        sb.append("Provide a helpful, specific answer. Use ₹ (INR) for any amounts. ");
        sb.append("If the question is unrelated to personal finance, politely redirect to financial topics. ");
        sb.append("Keep the answer concise but complete.");

        return sb.toString();
    }
}


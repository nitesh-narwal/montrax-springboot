package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ExpenceEntity;
import in.tracking.moneymanager.repository.ExpenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for smart financial insights using lightweight algorithms.
 * Optimized for low-resource servers (Oracle 1 CPU, Neon free tier).
 *
 * NO ML MODELS - Uses:
 * - Weighted moving averages for predictions
 * - Standard deviation for anomaly detection
 * - Gemini API for AI tips (offloads processing to Google)
 *
 * All algorithms use simple math operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartInsightsService {

    private final ExpenceRepository expenceRepository;
    private final ProfileService profileService;
    private final GeminiService geminiService;

    // Anomaly threshold: transactions > 2 std deviations from mean
    private static final double ANOMALY_THRESHOLD = 2.0;
    // Minimum data points needed for reliable predictions
    private static final int MIN_DATA_POINTS = 5;

    /**
     * Predict next month's spending based on historical data.
     * Uses 3-month weighted moving average (more recent = higher weight).
     *
     * Weights: Month-1 = 50%, Month-2 = 30%, Month-3 = 20%
     *
     * @return Map with predictions per category and overall
     */
    @Cacheable(value = "predictions", key = "'pred_' + #root.target.getProfileId()",
               unless = "#result == null")
    public Map<String, Object> predictNextMonthSpending() {
        Long profileId = getProfileId();
        LocalDate today = LocalDate.now();

        // Get last 3 months data
        List<Map<String, BigDecimal>> monthlyData = new ArrayList<>();
        BigDecimal[] monthTotals = new BigDecimal[3];

        for (int i = 1; i <= 3; i++) {
            YearMonth month = YearMonth.from(today).minusMonths(i);
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();

            List<ExpenceEntity> expenses = expenceRepository
                    .findByProfileIdAndDateBetween(profileId, start, end);

            // Group by category
            Map<String, BigDecimal> categoryTotals = expenses.stream()
                    .filter(e -> e.getCategory() != null)
                    .collect(Collectors.groupingBy(
                            e -> e.getCategory().getName(),
                            Collectors.reducing(BigDecimal.ZERO,
                                    ExpenceEntity::getAmount, BigDecimal::add)
                    ));

            // Calculate total for this month
            BigDecimal total = expenses.stream()
                    .map(ExpenceEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthTotals[i - 1] = total;
            monthlyData.add(categoryTotals);
        }

        // Calculate weighted average predictions
        // Weights: Month-1 = 0.5, Month-2 = 0.3, Month-3 = 0.2
        double[] weights = {0.5, 0.3, 0.2};

        // Get all categories across all months
        Set<String> allCategories = monthlyData.stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());

        // Calculate predictions per category
        Map<String, BigDecimal> categoryPredictions = new LinkedHashMap<>();
        for (String category : allCategories) {
            BigDecimal weightedSum = BigDecimal.ZERO;
            for (int i = 0; i < 3; i++) {
                BigDecimal value = monthlyData.get(i).getOrDefault(category, BigDecimal.ZERO);
                weightedSum = weightedSum.add(value.multiply(BigDecimal.valueOf(weights[i])));
            }
            categoryPredictions.put(category, weightedSum.setScale(2, RoundingMode.HALF_UP));
        }

        // Calculate total prediction
        BigDecimal totalPrediction = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            totalPrediction = totalPrediction.add(
                    monthTotals[i].multiply(BigDecimal.valueOf(weights[i])));
        }

        // Sort by predicted amount (highest first)
        categoryPredictions = categoryPredictions.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));

        // Calculate confidence based on data consistency
        String confidence = calculateConfidence(monthTotals);

        Map<String, Object> result = new HashMap<>();
        result.put("predictedTotal", totalPrediction.setScale(2, RoundingMode.HALF_UP));
        result.put("categoryPredictions", categoryPredictions);
        result.put("basedOnMonths", 3);
        result.put("confidence", confidence);
        result.put("nextMonth", YearMonth.from(today).plusMonths(1).toString());
        result.put("generatedAt", LocalDate.now().toString());

        return result;
    }

    /**
     * Detect anomalies in recent spending.
     * Flags transactions that are > 2 standard deviations from category mean.
     *
     * @return List of anomalies with details
     */
    @Cacheable(value = "anomalies", key = "'anom_' + #root.target.getProfileId()",
               unless = "#result == null || #result.isEmpty()")
    public List<Map<String, Object>> detectAnomalies() {
        Long profileId = getProfileId();
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        // Get historical data for baseline (exclude last 7 days)
        List<ExpenceEntity> historicalExpenses = expenceRepository
                .findByProfileIdAndDateBetween(profileId, threeMonthsAgo, today.minusDays(7));

        // Get recent transactions (last 7 days) to check for anomalies
        List<ExpenceEntity> recentExpenses = expenceRepository
                .findByProfileIdAndDateBetween(profileId, today.minusDays(7), today);

        // Calculate mean and std dev per category from historical data
        Map<String, List<BigDecimal>> categoryAmounts = historicalExpenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.mapping(ExpenceEntity::getAmount, Collectors.toList())
                ));

        // Calculate statistics for each category
        Map<String, double[]> categoryStats = new HashMap<>();
        for (Map.Entry<String, List<BigDecimal>> entry : categoryAmounts.entrySet()) {
            if (entry.getValue().size() >= MIN_DATA_POINTS) {
                double[] stats = calculateMeanAndStdDev(entry.getValue());
                categoryStats.put(entry.getKey(), stats);
            }
        }

        // Check recent transactions for anomalies
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (ExpenceEntity expense : recentExpenses) {
            if (expense.getCategory() == null) continue;

            String category = expense.getCategory().getName();
            double[] stats = categoryStats.get(category);

            if (stats != null && stats[1] > 0) {  // Has baseline data with non-zero std dev
                double amount = expense.getAmount().doubleValue();
                double mean = stats[0];
                double stdDev = stats[1];
                double zScore = Math.abs((amount - mean) / stdDev);

                if (zScore > ANOMALY_THRESHOLD) {
                    Map<String, Object> anomaly = new LinkedHashMap<>();
                    anomaly.put("transactionId", expense.getId());
                    anomaly.put("name", expense.getName());
                    anomaly.put("amount", expense.getAmount());
                    anomaly.put("category", category);
                    anomaly.put("date", expense.getDate().toString());
                    anomaly.put("categoryAverage", BigDecimal.valueOf(mean)
                            .setScale(2, RoundingMode.HALF_UP));
                    anomaly.put("percentageAboveAverage",
                            BigDecimal.valueOf((amount - mean) / mean * 100)
                                    .setScale(1, RoundingMode.HALF_UP));
                    anomaly.put("severity", zScore > 3 ? "HIGH" : "MEDIUM");
                    anomaly.put("reason", amount > mean
                            ? "Significantly higher than usual"
                            : "Significantly lower than usual");
                    anomalies.add(anomaly);
                }
            }
        }

        // Sort by severity (HIGH first) then by amount
        anomalies.sort((a, b) -> {
            int severityCompare = ((String) b.get("severity"))
                    .compareTo((String) a.get("severity"));
            if (severityCompare != 0) return severityCompare;
            return ((BigDecimal) b.get("amount"))
                    .compareTo((BigDecimal) a.get("amount"));
        });

        return anomalies;
    }

    /**
     * Get personalized money-saving tips using Gemini AI.
     * This counts towards user's monthly AI query limit.
     * Results are cached for 6 hours to reduce AI calls.
     *
     * @return AI-generated tips based on spending patterns
     */
    @Cacheable(value = "ai-tips", key = "'tips_' + #root.target.getProfileId()",
               unless = "#result == null")
    public Map<String, Object> getPersonalizedTips() {
        Long profileId = getProfileId();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        // Get current month spending by category
        List<ExpenceEntity> expenses = expenceRepository
                .findByProfileIdAndDateBetween(profileId,
                        currentMonth.atDay(1), today);

        Map<String, BigDecimal> categoryTotals = expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                ExpenceEntity::getAmount, BigDecimal::add)
                ));

        BigDecimal totalSpending = expenses.stream()
                .map(ExpenceEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Find top spending category
        String topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("General");

        BigDecimal topCategoryAmount = categoryTotals.getOrDefault(topCategory, BigDecimal.ZERO);

        // Build prompt for Gemini
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a personal finance advisor in India. ");
        prompt.append("Based on this month's spending, give exactly 3 specific money-saving tips.\n\n");
        prompt.append("Total spent so far this month: ₹").append(totalSpending).append("\n");
        prompt.append("Days into month: ").append(today.getDayOfMonth()).append("\n");
        prompt.append("Category breakdown:\n");

        // Sort categories by amount
        categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)  // Top 5 categories only
                .forEach(e -> prompt.append("- ").append(e.getKey())
                        .append(": ₹").append(e.getValue()).append("\n"));

        prompt.append("\nProvide practical, actionable tips specific to Indian context. ");
        prompt.append("Include specific amounts in ₹ where relevant. ");
        prompt.append("Keep each tip to 2-3 sentences max. ");
        prompt.append("Format as numbered list (1. 2. 3.)");

        String tips;
        try {
            tips = geminiService.generateResponse(prompt.toString());
        } catch (Exception e) {
            log.error("Failed to generate tips from Gemini: {}", e.getMessage());
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("rate limit")) {
                tips = "AI service is temporarily busy due to high demand. Please try again in a few minutes.";
            } else if (errorMsg != null && errorMsg.contains("authentication")) {
                tips = "AI service configuration issue. Please contact support.";
            } else if (errorMsg != null && errorMsg.contains("model not available")) {
                tips = "AI model needs updating. Please contact support.";
            } else {
                tips = "Unable to generate tips at this time. Please try again later.";
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tips", tips);
        result.put("basedOnSpending", totalSpending);
        result.put("topCategory", topCategory);
        result.put("topCategorySpending", topCategoryAmount);
        result.put("daysAnalyzed", today.getDayOfMonth());
        result.put("generatedAt", LocalDate.now().toString());

        return result;
    }

    /**
     * Get spending summary with basic insights (no AI).
     * Lighter alternative to AI tips.
     */
    public Map<String, Object> getSpendingSummary() {
        Long profileId = getProfileId();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth lastMonth = currentMonth.minusMonths(1);

        // Get current month expenses
        List<ExpenceEntity> currentExpenses = expenceRepository
                .findByProfileIdAndDateBetween(profileId,
                        currentMonth.atDay(1), today);

        // Get last month expenses
        List<ExpenceEntity> lastMonthExpenses = expenceRepository
                .findByProfileIdAndDateBetween(profileId,
                        lastMonth.atDay(1), lastMonth.atEndOfMonth());

        BigDecimal currentTotal = currentExpenses.stream()
                .map(ExpenceEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lastMonthTotal = lastMonthExpenses.stream()
                .map(ExpenceEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate daily average and projected
        int daysInMonth = currentMonth.lengthOfMonth();
        int daysPassed = today.getDayOfMonth();
        BigDecimal dailyAverage = daysPassed > 0
                ? currentTotal.divide(BigDecimal.valueOf(daysPassed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal projectedTotal = dailyAverage.multiply(BigDecimal.valueOf(daysInMonth));

        // Compare with last month
        BigDecimal difference = currentTotal.subtract(
                lastMonthTotal.multiply(BigDecimal.valueOf(daysPassed))
                        .divide(BigDecimal.valueOf(lastMonth.lengthOfMonth()), 2, RoundingMode.HALF_UP));
        String trend = difference.compareTo(BigDecimal.ZERO) > 0 ? "UP" :
                      difference.compareTo(BigDecimal.ZERO) < 0 ? "DOWN" : "SAME";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentMonthSpending", currentTotal);
        result.put("lastMonthSpending", lastMonthTotal);
        result.put("dailyAverage", dailyAverage);
        result.put("projectedMonthTotal", projectedTotal.setScale(2, RoundingMode.HALF_UP));
        result.put("comparedToLastMonth", difference.abs().setScale(2, RoundingMode.HALF_UP));
        result.put("trend", trend);
        result.put("daysPassed", daysPassed);
        result.put("daysRemaining", daysInMonth - daysPassed);

        return result;
    }

    /**
     * Helper method to get current user's profile ID.
     */
    public Long getProfileId() {
        return profileService.getCurrentProfile().getId();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate confidence score based on data consistency.
     */
    private String calculateConfidence(BigDecimal[] monthTotals) {
        // Check if all months have data
        boolean allHaveData = Arrays.stream(monthTotals)
                .allMatch(t -> t != null && t.compareTo(BigDecimal.ZERO) > 0);

        if (!allHaveData) return "LOW";

        // Check variance - if spending is consistent, higher confidence
        double mean = Arrays.stream(monthTotals)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);

        if (mean == 0) return "LOW";

        double variance = Arrays.stream(monthTotals)
                .mapToDouble(t -> Math.pow(t.doubleValue() - mean, 2))
                .average()
                .orElse(0);

        double coefficientOfVariation = Math.sqrt(variance) / mean;

        if (coefficientOfVariation < 0.2) return "HIGH";
        if (coefficientOfVariation < 0.4) return "MEDIUM";
        return "LOW";
    }

    /**
     * Calculate mean and standard deviation for a list of amounts.
     * Returns [mean, stdDev]
     */
    private double[] calculateMeanAndStdDev(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return new double[]{0, 0};
        }

        double sum = amounts.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        double mean = sum / amounts.size();

        double variance = amounts.stream()
                .mapToDouble(a -> Math.pow(a.doubleValue() - mean, 2))
                .sum() / amounts.size();
        double stdDev = Math.sqrt(variance);

        return new double[]{mean, stdDev};
    }
}


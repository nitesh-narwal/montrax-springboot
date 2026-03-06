package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.AnalyticsDTO;
import in.tracking.moneymanager.dto.AnalyticsDTO.DailyData;
import in.tracking.moneymanager.dto.AnalyticsDTO.MonthlyData;
import in.tracking.moneymanager.entity.ExpenceEntity;
import in.tracking.moneymanager.entity.IncomeEntity;
import in.tracking.moneymanager.repository.ExpenceRepository;
import in.tracking.moneymanager.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating analytics data for dashboard charts.
 * Uses database aggregations for efficiency on low-resource servers.
 * Results are cached to reduce database load on Neon free tier.
 *
 * Safety: All queries are limited to 1 year max to prevent heavy loads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ExpenceRepository expenceRepository;
    private final IncomeRepository incomeRepository;
    private final ProfileService profileService;

    // Maximum date range allowed (365 days) - protects Neon free tier
    private static final int MAX_DAYS_RANGE = 365;

    /**
     * Get analytics for a specific date range.
     * Used for weekly/monthly/custom range charts.
     *
     * @param startDate Start of range
     * @param endDate End of range
     * @return Analytics data for charts
     */
    @Cacheable(value = "analytics", key = "'analytics_' + #root.target.getProfileId() + '_' + #startDate + '_' + #endDate",
               unless = "#result == null")
    public AnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
        // Safety: Limit date range to prevent heavy queries
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > MAX_DAYS_RANGE) {
            startDate = endDate.minusDays(MAX_DAYS_RANGE);
            log.warn("Date range limited to {} days for performance", MAX_DAYS_RANGE);
        }

        Long profileId = getProfileId();

        // Fetch all expenses and incomes in date range
        List<ExpenceEntity> expenses = expenceRepository
                .findByProfileIdAndDateBetween(profileId, startDate, endDate);
        List<IncomeEntity> incomes = incomeRepository
                .findByProfileIdAndDateBetween(profileId, startDate, endDate);

        // Calculate totals
        BigDecimal totalExpense = calculateTotalExpense(expenses);
        BigDecimal totalIncome = calculateTotalIncome(incomes);
        BigDecimal netSavings = totalIncome.subtract(totalExpense);

        // Calculate savings rate
        Double savingsRate = totalIncome.compareTo(BigDecimal.ZERO) > 0
                ? netSavings.multiply(BigDecimal.valueOf(100))
                        .divide(totalIncome, 2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        // Build analytics
        return AnalyticsDTO.builder()
                .dailySpending(buildDailyExpenseData(expenses, startDate, endDate))
                .dailyIncome(buildDailyIncomeData(incomes, startDate, endDate))
                .categoryBreakdown(buildCategoryBreakdown(expenses))
                .monthlyTrends(buildMonthlyTrends(expenses, incomes, startDate, endDate))
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netSavings(netSavings)
                .averageDailySpending(calculateAverageDaily(expenses, startDate, endDate))
                .highestSpendingDay(findHighestSpendingDay(expenses))
                .topSpendingCategory(findTopCategory(expenses))
                .savingsRate(savingsRate)
                .build();
    }

    /**
     * Get current week analytics (last 7 days).
     * Good for weekly spending trends chart.
     */
    public AnalyticsDTO getWeeklyAnalytics() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);
        return getAnalytics(startDate, endDate);
    }

    /**
     * Get current month analytics.
     * Good for monthly overview dashboard.
     */
    public AnalyticsDTO getMonthlyAnalytics() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.withDayOfMonth(1);
        return getAnalytics(startDate, endDate);
    }

    /**
     * Get last 6 months analytics for trends.
     * Good for long-term trend analysis.
     */
    public AnalyticsDTO getSixMonthAnalytics() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(6).withDayOfMonth(1);
        return getAnalytics(startDate, endDate);
    }

    /**
     * Get year-to-date analytics.
     * Good for annual financial summary.
     */
    public AnalyticsDTO getYearlyAnalytics() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.withDayOfYear(1);
        return getAnalytics(startDate, endDate);
    }

    /**
     * Helper method for cache key - gets current user's profile ID.
     */
    public Long getProfileId() {
        return profileService.getCurrentProfile().getId();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Build daily spending data for line/bar charts.
     * Fills in zero for days with no spending.
     */
    private List<DailyData> buildDailyExpenseData(List<ExpenceEntity> expenses,
                                                   LocalDate start, LocalDate end) {
        // Group expenses by date
        Map<LocalDate, BigDecimal> dailyTotals = expenses.stream()
                .collect(Collectors.groupingBy(
                        ExpenceEntity::getDate,
                        Collectors.reducing(BigDecimal.ZERO,
                                ExpenceEntity::getAmount, BigDecimal::add)
                ));

        // Fill in all dates in range
        List<DailyData> result = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            result.add(DailyData.builder()
                    .date(current)
                    .amount(dailyTotals.getOrDefault(current, BigDecimal.ZERO))
                    .build());
            current = current.plusDays(1);
        }
        return result;
    }

    /**
     * Build daily income data for charts.
     */
    private List<DailyData> buildDailyIncomeData(List<IncomeEntity> incomes,
                                                  LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> dailyTotals = incomes.stream()
                .collect(Collectors.groupingBy(
                        IncomeEntity::getDate,
                        Collectors.reducing(BigDecimal.ZERO,
                                IncomeEntity::getAmount, BigDecimal::add)
                ));

        List<DailyData> result = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            result.add(DailyData.builder()
                    .date(current)
                    .amount(dailyTotals.getOrDefault(current, BigDecimal.ZERO))
                    .build());
            current = current.plusDays(1);
        }
        return result;
    }

    /**
     * Build category breakdown for pie/donut charts.
     */
    private Map<String, BigDecimal> buildCategoryBreakdown(List<ExpenceEntity> expenses) {
        return expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        LinkedHashMap::new,  // Preserve insertion order
                        Collectors.reducing(BigDecimal.ZERO,
                                ExpenceEntity::getAmount, BigDecimal::add)
                ));
    }

    /**
     * Build monthly trends for bar charts.
     */
    private List<MonthlyData> buildMonthlyTrends(List<ExpenceEntity> expenses,
                                                  List<IncomeEntity> incomes,
                                                  LocalDate start, LocalDate end) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // Group by month
        Map<String, BigDecimal> monthlyExpenses = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDate().format(formatter),
                        Collectors.reducing(BigDecimal.ZERO,
                                ExpenceEntity::getAmount, BigDecimal::add)
                ));

        Map<String, BigDecimal> monthlyIncomes = incomes.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getDate().format(formatter),
                        Collectors.reducing(BigDecimal.ZERO,
                                IncomeEntity::getAmount, BigDecimal::add)
                ));

        // Build list of all months in range
        List<MonthlyData> result = new ArrayList<>();
        YearMonth current = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(end);

        while (!current.isAfter(endMonth)) {
            String monthKey = current.format(formatter);
            BigDecimal income = monthlyIncomes.getOrDefault(monthKey, BigDecimal.ZERO);
            BigDecimal expense = monthlyExpenses.getOrDefault(monthKey, BigDecimal.ZERO);

            result.add(MonthlyData.builder()
                    .month(monthKey)
                    .income(income)
                    .expense(expense)
                    .savings(income.subtract(expense))
                    .build());

            current = current.plusMonths(1);
        }
        return result;
    }

    private BigDecimal calculateTotalIncome(List<IncomeEntity> incomes) {
        return incomes.stream()
                .map(IncomeEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalExpense(List<ExpenceEntity> expenses) {
        return expenses.stream()
                .map(ExpenceEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateAverageDaily(List<ExpenceEntity> expenses,
                                              LocalDate start, LocalDate end) {
        BigDecimal total = calculateTotalExpense(expenses);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal findHighestSpendingDay(List<ExpenceEntity> expenses) {
        return expenses.stream()
                .collect(Collectors.groupingBy(
                        ExpenceEntity::getDate,
                        Collectors.reducing(BigDecimal.ZERO,
                                ExpenceEntity::getAmount, BigDecimal::add)
                ))
                .values().stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private String findTopCategory(List<ExpenceEntity> expenses) {
        return expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                ExpenceEntity::getAmount, BigDecimal::add)
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }
}


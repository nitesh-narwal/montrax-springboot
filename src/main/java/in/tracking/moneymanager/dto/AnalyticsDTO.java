package in.tracking.moneymanager.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO for analytics data used by frontend charts.
 * Contains aggregated spending/income data for visualization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDTO {

    // Daily spending data for trend line charts
    private List<DailyData> dailySpending;

    // Daily income data for trend line charts
    private List<DailyData> dailyIncome;

    // Category-wise breakdown for pie/donut charts
    private Map<String, BigDecimal> categoryBreakdown;

    // Month-wise totals for bar charts
    private List<MonthlyData> monthlyTrends;

    // Summary statistics
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netSavings;
    private BigDecimal averageDailySpending;
    private BigDecimal highestSpendingDay;
    private String topSpendingCategory;
    private Double savingsRate;

    /**
     * Inner class for daily data points (for line/area charts).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyData {
        private LocalDate date;
        private BigDecimal amount;
    }

    /**
     * Inner class for monthly data points (for bar charts).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyData {
        private String month;      // Format: "2026-01", "2026-02"
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal savings;
    }
}


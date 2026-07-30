package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.AnalyticsDTO;
import in.tracking.moneymanager.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controller for dashboard analytics APIs.
 * Provides aggregated data for frontend charts (Chart.js, etc.).
 *
 * Endpoints:
 * - GET /api/analytics/weekly - Last 7 days data
 * - GET /api/analytics/monthly - Current month data
 * - GET /api/analytics/6months - Last 6 months trends
 * - GET /api/analytics/yearly - Year-to-date data
 * - GET /api/analytics/custom - Custom date range (max 365 days)
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Get weekly analytics (last 7 days).
     * Best for: Weekly spending trends chart, recent activity.
     */
    @GetMapping("/weekly")
    public ResponseEntity<AnalyticsDTO> getWeeklyAnalytics() {
        return ResponseEntity.ok(analyticsService.getWeeklyAnalytics());
    }

    /**
     * Get monthly analytics (current month).
     * Best for: Monthly overview dashboard, category pie chart.
     */
    @GetMapping("/monthly")
    public ResponseEntity<AnalyticsDTO> getMonthlyAnalytics() {
        return ResponseEntity.ok(analyticsService.getMonthlyAnalytics());
    }

    /**
     * Get 6-month analytics.
     * Best for: Long-term trend analysis, monthly comparison bar chart.
     */
    @GetMapping("/6months")
    public ResponseEntity<AnalyticsDTO> getSixMonthAnalytics() {
        return ResponseEntity.ok(analyticsService.getSixMonthAnalytics());
    }

    /**
     * Get yearly analytics (year-to-date).
     * Best for: Annual financial summary, year overview.
     */
    @GetMapping("/yearly")
    public ResponseEntity<AnalyticsDTO> getYearlyAnalytics() {
        return ResponseEntity.ok(analyticsService.getYearlyAnalytics());
    }

    /**
     * Get analytics for custom date range.
     * Limited to 365 days max to protect database.
     *
     * @param startDate Start date (format: yyyy-MM-dd)
     * @param endDate End date (format: yyyy-MM-dd)
     *
     * Example: /api/analytics/custom?startDate=2026-01-01&endDate=2026-03-01
     */
    @GetMapping("/custom")
    public ResponseEntity<AnalyticsDTO> getCustomAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }

        return ResponseEntity.ok(analyticsService.getAnalytics(startDate, endDate));
    }
}


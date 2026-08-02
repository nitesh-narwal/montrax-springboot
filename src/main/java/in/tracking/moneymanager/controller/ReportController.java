package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Downloadable PDF/Excel financial reports for a date range.
 * Reuses the same aggregation AnalyticsController already exposes for charts.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final int MAX_DAYS_RANGE = 365;

    private final ReportService reportService;

    /**
     * GET /api/reports/export?format=pdf|excel&startDate=&endDate=
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam String format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_DAYS_RANGE) {
            throw new IllegalArgumentException("Date range cannot exceed " + MAX_DAYS_RANGE + " days");
        }

        byte[] fileBytes;
        MediaType contentType;
        String extension;

        switch (format.toLowerCase()) {
            case "pdf" -> {
                fileBytes = reportService.generatePdfReport(startDate, endDate);
                contentType = MediaType.APPLICATION_PDF;
                extension = "pdf";
            }
            case "excel" -> {
                fileBytes = reportService.generateExcelReport(startDate, endDate);
                contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                extension = "xlsx";
            }
            default -> throw new IllegalArgumentException("Unsupported format: " + format + " (use 'pdf' or 'excel')");
        }

        String filename = "report-" + startDate + "-to-" + endDate + "." + extension;

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(fileBytes);
    }
}

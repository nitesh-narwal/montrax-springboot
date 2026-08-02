package in.tracking.moneymanager.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import in.tracking.moneymanager.dto.AnalyticsDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * Generates downloadable PDF/Excel financial reports from the same aggregated
 * data AnalyticsService already computes for the frontend charts - no separate
 * queries, so a report is always consistent with what the dashboard shows.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AnalyticsService analyticsService;

    public byte[] generatePdfReport(LocalDate startDate, LocalDate endDate) {
        AnalyticsDTO analytics = analyticsService.getAnalytics(startDate, endDate);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
             Document document = new Document(pdfDoc)) {

            document.add(new Paragraph("Financial Report").setBold().setFontSize(20));
            document.add(new Paragraph(startDate + " to " + endDate).setFontColor(ColorConstants.GRAY));

            Table summary = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            addSummaryRow(summary, "Total Income", analytics.getTotalIncome());
            addSummaryRow(summary, "Total Expense", analytics.getTotalExpense());
            addSummaryRow(summary, "Net Savings", analytics.getNetSavings());
            addSummaryRow(summary, "Savings Rate", analytics.getSavingsRate() + "%");
            addSummaryRow(summary, "Average Daily Spending", analytics.getAverageDailySpending());
            addSummaryRow(summary, "Top Spending Category",
                    analytics.getTopSpendingCategory() != null ? analytics.getTopSpendingCategory() : "N/A");
            document.add(summary);

            document.add(new Paragraph("Category Breakdown").setBold().setFontSize(14).setMarginTop(16));
            Table categoryTable = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth();
            categoryTable.addHeaderCell(new Cell().add(new Paragraph("Category").setBold()));
            categoryTable.addHeaderCell(new Cell().add(new Paragraph("Amount").setBold()));
            for (Map.Entry<String, java.math.BigDecimal> entry : analytics.getCategoryBreakdown().entrySet()) {
                categoryTable.addCell(entry.getKey());
                categoryTable.addCell(entry.getValue().toPlainString());
            }
            document.add(categoryTable);

            document.add(new Paragraph("Monthly Trends").setBold().setFontSize(14).setMarginTop(16));
            Table monthlyTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1})).useAllAvailableWidth();
            for (String header : new String[]{"Month", "Income", "Expense", "Savings"}) {
                monthlyTable.addHeaderCell(new Cell().add(new Paragraph(header).setBold()));
            }
            for (AnalyticsDTO.MonthlyData m : analytics.getMonthlyTrends()) {
                monthlyTable.addCell(m.getMonth());
                monthlyTable.addCell(m.getIncome().toPlainString());
                monthlyTable.addCell(m.getExpense().toPlainString());
                monthlyTable.addCell(m.getSavings().toPlainString());
            }
            document.add(monthlyTable);
        }
        return out.toByteArray();
    }

    public byte[] generateExcelReport(LocalDate startDate, LocalDate endDate) {
        AnalyticsDTO analytics = analyticsService.getAnalytics(startDate, endDate);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            XSSFSheet summarySheet = workbook.createSheet("Summary");
            int rowIdx = 0;
            rowIdx = writeSummaryRow(summarySheet, rowIdx, headerStyle, "Report Range", startDate + " to " + endDate);
            rowIdx = writeSummaryRow(summarySheet, rowIdx, headerStyle, "Total Income", analytics.getTotalIncome().toPlainString());
            rowIdx = writeSummaryRow(summarySheet, rowIdx, headerStyle, "Total Expense", analytics.getTotalExpense().toPlainString());
            rowIdx = writeSummaryRow(summarySheet, rowIdx, headerStyle, "Net Savings", analytics.getNetSavings().toPlainString());
            rowIdx = writeSummaryRow(summarySheet, rowIdx, headerStyle, "Savings Rate", analytics.getSavingsRate() + "%");
            writeSummaryRow(summarySheet, rowIdx, headerStyle, "Average Daily Spending", analytics.getAverageDailySpending().toPlainString());
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            XSSFSheet categorySheet = workbook.createSheet("Category Breakdown");
            Row categoryHeader = categorySheet.createRow(0);
            writeHeaderCell(categoryHeader, 0, "Category", headerStyle);
            writeHeaderCell(categoryHeader, 1, "Amount", headerStyle);
            int catRow = 1;
            for (Map.Entry<String, java.math.BigDecimal> entry : analytics.getCategoryBreakdown().entrySet()) {
                Row row = categorySheet.createRow(catRow++);
                row.createCell(0, CellType.STRING).setCellValue(entry.getKey());
                row.createCell(1, CellType.NUMERIC).setCellValue(entry.getValue().doubleValue());
            }
            categorySheet.autoSizeColumn(0);
            categorySheet.autoSizeColumn(1);

            XSSFSheet monthlySheet = workbook.createSheet("Monthly Trends");
            Row monthlyHeader = monthlySheet.createRow(0);
            String[] headers = {"Month", "Income", "Expense", "Savings"};
            for (int i = 0; i < headers.length; i++) {
                writeHeaderCell(monthlyHeader, i, headers[i], headerStyle);
            }
            int monthRow = 1;
            for (AnalyticsDTO.MonthlyData m : analytics.getMonthlyTrends()) {
                Row row = monthlySheet.createRow(monthRow++);
                row.createCell(0, CellType.STRING).setCellValue(m.getMonth());
                row.createCell(1, CellType.NUMERIC).setCellValue(m.getIncome().doubleValue());
                row.createCell(2, CellType.NUMERIC).setCellValue(m.getExpense().doubleValue());
                row.createCell(3, CellType.NUMERIC).setCellValue(m.getSavings().doubleValue());
            }
            for (int i = 0; i < headers.length; i++) {
                monthlySheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Excel report", e);
        }
    }

    private void addSummaryRow(Table table, String label, Object value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold()));
        table.addCell(new Paragraph(String.valueOf(value)));
    }

    private int writeSummaryRow(XSSFSheet sheet, int rowIdx, CellStyle headerStyle, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        var labelCell = row.createCell(0, CellType.STRING);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(headerStyle);
        row.createCell(1, CellType.STRING).setCellValue(value);
        return rowIdx + 1;
    }

    private void writeHeaderCell(Row row, int col, String value, CellStyle style) {
        var cell = row.createCell(col, CellType.STRING);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}

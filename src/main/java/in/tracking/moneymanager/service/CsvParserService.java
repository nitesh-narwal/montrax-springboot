package in.tracking.moneymanager.service;

import com.opencsv.CSVReader;
import in.tracking.moneymanager.entity.BankTransactionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for parsing bank CSV files.
 * Supports multiple Indian bank formats: SBI, HDFC, ICICI, AXIS, KOTAK.
 *
 * Features:
 * - Auto-detects column positions from headers
 * - Handles multiple date formats
 * - Flexible parsing for various CSV structures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvParserService {

    private final MerchantCategorizationService categorizationService;

    // Common date formats used by Indian banks
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    /**
     * Parse CSV file and return list of bank transactions.
     * Auto-detects column positions from headers.
     *
     * @param file Uploaded CSV file
     * @param profileId User's profile ID
     * @param bankName Bank name for categorization
     * @return List of parsed transactions
     */
    public List<BankTransactionEntity> parseCSV(MultipartFile file, Long profileId, String bankName) {
        List<BankTransactionEntity> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();

            if (rows.isEmpty()) {
                throw new RuntimeException("CSV file is empty");
            }

            log.info("Parsing {} rows from {} CSV", rows.size(), bankName);

            // Log first few rows for debugging
            for (int i = 0; i < Math.min(3, rows.size()); i++) {
                log.info("Row {}: {}", i, String.join(" | ", rows.get(i)));
            }

            // Find header row and detect column mapping
            int headerRow = findHeaderRow(rows);
            Map<String, Integer> columnMap = detectColumns(rows.get(headerRow));

            log.info("Detected header at row {}: {}", headerRow, String.join(", ", rows.get(headerRow)));
            log.info("Column mapping: {}", columnMap);

            // Parse each data row
            for (int i = headerRow + 1; i < rows.size(); i++) {
                String[] row = rows.get(i);

                // Skip empty rows
                if (isEmptyRow(row)) {
                    continue;
                }

                try {
                    BankTransactionEntity transaction = parseRowWithColumnMap(row, columnMap, bankName, profileId);
                    if (transaction != null && transaction.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                        transactions.add(transaction);
                        log.debug("Parsed transaction: date={}, desc={}, amount={}, type={}",
                            transaction.getTransactionDate(), transaction.getDescription(),
                            transaction.getAmount(), transaction.getType());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse row {}: {} - Row data: {}", i, e.getMessage(), String.join("|", row));
                }
            }

            log.info("Successfully parsed {} transactions from CSV", transactions.size());

        } catch (Exception e) {
            log.error("Failed to parse CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage());
        }

        return transactions;
    }

    /**
     * Check if a row is empty or contains only whitespace.
     */
    private boolean isEmptyRow(String[] row) {
        if (row == null || row.length == 0) return true;
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find the header row (usually contains column names).
     */
    private int findHeaderRow(List<String[]> rows) {
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String[] row = rows.get(i);
            String joined = String.join(" ", row).toLowerCase();

            // Check if row contains typical header keywords
            if (joined.contains("date") || joined.contains("description") ||
                joined.contains("debit") || joined.contains("credit") ||
                joined.contains("withdrawal") || joined.contains("deposit") ||
                joined.contains("narration") || joined.contains("particulars") ||
                joined.contains("txn") || joined.contains("transaction") ||
                joined.contains("amount") || joined.contains("balance")) {
                return i;
            }
        }
        return 0; // Default to first row
    }

    /**
     * Auto-detect column positions from header row.
     */
    private Map<String, Integer> detectColumns(String[] headers) {
        Map<String, Integer> columnMap = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();

            // Date columns
            if ((header.contains("date") || header.contains("txn") || header.equals("dt")) &&
                !header.contains("value") && columnMap.get("date") == null) {
                columnMap.put("date", i);
            } else if (header.contains("value") && header.contains("date")) {
                columnMap.put("valueDate", i);
            }

            // Description columns
            if (header.contains("description") || header.contains("narration") ||
                header.contains("particular") || header.contains("remark") ||
                header.contains("detail")) {
                columnMap.put("description", i);
            }

            // Reference columns
            if (header.contains("ref") || header.contains("chq") || header.contains("cheque") ||
                header.contains("utr") || header.contains("reference") || header.contains("no.")) {
                columnMap.put("reference", i);
            }

            // Amount columns - be more flexible
            if (header.contains("debit") || header.contains("withdrawal") ||
                header.equals("dr") || header.contains("dr.") || header.contains("spent")) {
                columnMap.put("debit", i);
            }
            if (header.contains("credit") || header.contains("deposit") ||
                header.equals("cr") || header.contains("cr.") || header.contains("received")) {
                columnMap.put("credit", i);
            }
            if (header.equals("amount") || header.equals("amt") || header.equals("value")) {
                columnMap.put("amount", i);
            }

            // Balance column
            if (header.contains("balance") || header.contains("bal") || header.contains("closing")) {
                columnMap.put("balance", i);
            }
        }

        // If no date column found, use first column
        if (columnMap.get("date") == null) {
            columnMap.put("date", 0);
        }

        // If no description found, try second column
        if (columnMap.get("description") == null && headers.length > 1) {
            // Find first text column that's not date/amount
            for (int i = 1; i < headers.length; i++) {
                String header = headers[i].toLowerCase();
                if (!header.contains("date") && !header.contains("debit") &&
                    !header.contains("credit") && !header.contains("balance") &&
                    !header.contains("amount") && !header.matches(".*\\d.*")) {
                    columnMap.put("description", i);
                    break;
                }
            }
        }

        return columnMap;
    }

    /**
     * Parse row using detected column mapping.
     */
    private BankTransactionEntity parseRowWithColumnMap(String[] row, Map<String, Integer> columnMap,
                                                         String bankName, Long profileId) {
        // Clean all cells
        for (int i = 0; i < row.length; i++) {
            row[i] = row[i] != null ? row[i].trim() : "";
        }

        // Extract values using column map
        LocalDate date = null;
        String description = "";
        String refNo = "";
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;

        // Date
        Integer dateCol = columnMap.get("date");
        if (dateCol != null && dateCol < row.length) {
            date = parseDate(row[dateCol]);
        }
        if (date == null) {
            Integer valueDateCol = columnMap.get("valueDate");
            if (valueDateCol != null && valueDateCol < row.length) {
                date = parseDate(row[valueDateCol]);
            }
        }

        // If still no valid date, try to find a date in any column
        if (date == null) {
            for (String cell : row) {
                if (looksLikeDate(cell)) {
                    LocalDate parsed = parseDate(cell);
                    if (parsed != null) {
                        date = parsed;
                        break;
                    }
                }
            }
        }

        // Description
        Integer descCol = columnMap.get("description");
        if (descCol != null && descCol < row.length) {
            description = row[descCol];
        }

        // If description is empty, try to find the longest non-numeric cell
        if (description.isEmpty()) {
            for (String cell : row) {
                if (cell.length() > description.length() && !isNumeric(cell) && !looksLikeDate(cell)) {
                    description = cell;
                }
            }
        }

        // Reference
        Integer refCol = columnMap.get("reference");
        if (refCol != null && refCol < row.length) {
            refNo = row[refCol];
        }

        // Debit/Credit
        Integer debitCol = columnMap.get("debit");
        Integer creditCol = columnMap.get("credit");
        Integer amountCol = columnMap.get("amount");

        if (debitCol != null && debitCol < row.length) {
            debit = parseAmount(row[debitCol]);
        }
        if (creditCol != null && creditCol < row.length) {
            credit = parseAmount(row[creditCol]);
        }

        // If only amount column exists
        if (amountCol != null && amountCol < row.length && debit.equals(BigDecimal.ZERO) && credit.equals(BigDecimal.ZERO)) {
            String amountStr = row[amountCol];
            BigDecimal amount = parseAmount(amountStr);

            // Check for negative sign or DR indicator
            String rowJoined = String.join(" ", row).toLowerCase();
            if (amountStr.contains("-") || rowJoined.contains(" dr") ||
                rowJoined.contains("debit") || rowJoined.contains("withdrawal")) {
                debit = amount;
            } else {
                credit = amount;
            }
        }

        // If still no debit/credit, try to find numeric columns
        if (debit.equals(BigDecimal.ZERO) && credit.equals(BigDecimal.ZERO)) {
            List<BigDecimal> amounts = new ArrayList<>();
            Integer balColIdx = columnMap.get("balance");
            for (int i = 0; i < row.length; i++) {
                if (isNumeric(row[i]) && (balColIdx == null || i != balColIdx)) {
                    BigDecimal amt = parseAmount(row[i]);
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        amounts.add(amt);
                    }
                }
            }
            if (!amounts.isEmpty()) {
                // First non-zero amount found
                BigDecimal amount = amounts.get(0);
                String rowJoined = String.join(" ", row).toLowerCase();
                if (rowJoined.contains("cr") || rowJoined.contains("credit") || rowJoined.contains("deposit")) {
                    credit = amount;
                } else {
                    debit = amount;
                }
            }
        }

        // Balance
        Integer balCol = columnMap.get("balance");
        if (balCol != null && balCol < row.length) {
            balance = parseAmount(row[balCol]);
        }

        // Skip if no meaningful data
        if (description.isEmpty() && debit.equals(BigDecimal.ZERO) && credit.equals(BigDecimal.ZERO)) {
            log.debug("Skipping row - no meaningful data: {}", String.join("|", row));
            return null;
        }

        return buildTransaction(profileId, bankName, date != null ? date : LocalDate.now(),
                               description, refNo, debit, credit, balance);
    }

    /**
     * Check if string looks numeric (for amount detection).
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        String cleaned = str.replaceAll("[,₹$\\s]", "").trim();
        return cleaned.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Check if a string looks like a date.
     */
    private boolean looksLikeDate(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        str = str.trim();
        return str.matches("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}") ||
               str.matches("\\d{1,2}[/\\-][A-Za-z]{3}[/\\-]\\d{2,4}") ||
               str.matches("\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{2,4}") ||
               str.matches("\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2}");
    }

    /**
     * Build transaction entity from parsed values.
     */
    private BankTransactionEntity buildTransaction(Long profileId, String bankName,
                                                    LocalDate date, String description, String refNo,
                                                    BigDecimal debit, BigDecimal credit, BigDecimal balance) {
        // Determine transaction type and amount
        String type;
        BigDecimal amount;

        if (credit.compareTo(BigDecimal.ZERO) > 0) {
            type = "CREDIT";
            amount = credit;
        } else if (debit.compareTo(BigDecimal.ZERO) > 0) {
            type = "DEBIT";
            amount = debit;
        } else {
            return null; // Skip zero-amount transactions
        }

        // Auto-categorize and extract merchant
        String suggestedCategory = categorizationService.suggestCategory(description);
        String merchantName = categorizationService.extractMerchantName(description);

        return BankTransactionEntity.builder()
                .profileId(profileId)
                .bankName(bankName)
                .transactionDate(date)
                .description(description)
                .referenceNumber(refNo)
                .amount(amount)
                .type(type)
                .balance(balance)
                .suggestedCategory(suggestedCategory)
                .merchantName(merchantName)
                .isConverted(false)
                .build();
    }

    /**
     * Parse date from various formats.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        dateStr = dateStr.trim();

        // Try each format
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception ignored) {
            }
        }

        log.warn("Failed to parse date: {}", dateStr);
        return null;
    }

    /**
     * Parse amount, handling commas, currency symbols, and empty values.
     */
    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty() || amountStr.equals("-")) {
            return BigDecimal.ZERO;
        }

        // Remove commas, currency symbols, spaces, and other non-numeric chars (except . and -)
        String cleaned = amountStr
                .replaceAll("[,₹$\\s]", "")
                .replaceAll("[^0-9.\\-]", "")
                .trim();

        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(cleaned).abs(); // Always return positive
        } catch (Exception e) {
            log.warn("Failed to parse amount: {} -> {}", amountStr, cleaned);
            return BigDecimal.ZERO;
        }
    }
}


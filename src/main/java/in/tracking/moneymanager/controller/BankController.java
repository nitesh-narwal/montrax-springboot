package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.annotation.PremiumFeature;
import in.tracking.moneymanager.dto.BankTransactionDTO;
import in.tracking.moneymanager.service.BankTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for bank CSV import and transaction management.
 * All endpoints require BASIC or PREMIUM subscription.
 *
 * Endpoints:
 * - POST /api/bank/import - Import transactions from CSV [BASIC+]
 * - GET /api/bank/transactions - Get imported transactions [BASIC+]
 * - GET /api/bank/transactions/unconverted - Get unconverted transactions [BASIC+]
 * - POST /api/bank/transactions/{id}/categorize - Update category [BASIC+]
 * - GET /api/bank/import-usage - Get import usage stats
 *
 * Supported Banks: SBI, HDFC, ICICI, AXIS, KOTAK, GENERIC
 */
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankController {

    private final BankTransactionService bankTransactionService;

    /**
     * Import transactions from CSV file.
     * Automatically categorizes transactions based on merchant patterns.
     *
     * @param file CSV file from bank statement
     * @param bankName Bank name: SBI, HDFC, ICICI, AXIS, KOTAK, or GENERIC
     * @return Import result with count of imported transactions
     */
    @PostMapping("/import")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "CSV Bank Statement Import")
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankName") String bankName) {

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please select a CSV file to upload"
            ));
        }

        // Validate file type
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please upload a CSV file"
            ));
        }

        // Validate bank name
        String normalizedBank = bankName.toUpperCase();
        if (!List.of("SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "GENERIC").contains(normalizedBank)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Unsupported bank. Supported banks: SBI, HDFC, ICICI, AXIS, KOTAK, GENERIC"
            ));
        }

        Map<String, Object> result = bankTransactionService.importCsv(file, normalizedBank);
        return ResponseEntity.ok(result);
    }

    /**
     * Get bank transactions with pagination.
     *
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of bank transactions
     */
    @GetMapping("/transactions")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Bank Transactions")
    public ResponseEntity<Page<BankTransactionDTO>> getTransactions(Pageable pageable) {
        return ResponseEntity.ok(bankTransactionService.getTransactions(pageable));
    }

    /**
     * Get transactions that haven't been converted to expense/income yet.
     * Useful for reviewing and converting imported transactions.
     *
     * @return List of unconverted transactions
     */
    @GetMapping("/transactions/unconverted")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Bank Transactions")
    public ResponseEntity<List<BankTransactionDTO>> getUnconvertedTransactions() {
        return ResponseEntity.ok(bankTransactionService.getUnconvertedTransactions());
    }

    /**
     * Update category for a bank transaction and convert to expense/income.
     *
     * @param id Transaction ID
     * @param categoryId New category ID
     * @return Updated transaction
     */
    @PostMapping("/transactions/{id}/categorize")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Transaction Categorization")
    public ResponseEntity<BankTransactionDTO> categorizeTransaction(
            @PathVariable Long id,
            @RequestParam Long categoryId) {
        return ResponseEntity.ok(bankTransactionService.categorizeTransaction(id, categoryId));
    }

    /**
     * Delete a single bank transaction.
     *
     * @param id Transaction ID to delete
     * @return Success response
     */
    @DeleteMapping("/transactions/{id}")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Bank Transactions")
    public ResponseEntity<Map<String, Object>> deleteTransaction(@PathVariable Long id) {
        boolean deleted = bankTransactionService.deleteTransaction(id);
        return ResponseEntity.ok(Map.of(
                "success", deleted,
                "message", "Transaction deleted successfully"
        ));
    }

    /**
     * Delete multiple bank transactions.
     *
     * @param ids List of transaction IDs to delete
     * @return Number of deleted transactions
     */
    @DeleteMapping("/transactions")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Bank Transactions")
    public ResponseEntity<Map<String, Object>> deleteTransactions(@RequestBody List<Long> ids) {
        int deleted = bankTransactionService.deleteTransactions(ids);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedCount", deleted,
                "message", "Deleted " + deleted + " transactions"
        ));
    }

    /**
     * Delete all unconverted bank transactions.
     *
     * @return Number of deleted transactions
     */
    @DeleteMapping("/transactions/unconverted")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Bank Transactions")
    public ResponseEntity<Map<String, Object>> deleteAllUnconverted() {
        int deleted = bankTransactionService.deleteAllUnconverted();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedCount", deleted,
                "message", "Deleted " + deleted + " unconverted transactions"
        ));
    }

    /**
     * Get CSV import usage statistics for current month.
     * Shows how many imports have been used vs the limit.
     *
     * @return Usage statistics
     */
    @GetMapping("/import-usage")
    public ResponseEntity<Map<String, Object>> getImportUsage() {
        return ResponseEntity.ok(bankTransactionService.getImportUsage());
    }

    @GetMapping("/transactions/range")
    @PremiumFeature(requiredPlans = {"BASIC", "PREMIUM"}, featureName = "Bank Transactions")
    public ResponseEntity<?> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {

        try {
            return ResponseEntity.ok(
                    bankTransactionService.getTransactionsByDateRange(startDate, endDate, pageable));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        }
    }

}


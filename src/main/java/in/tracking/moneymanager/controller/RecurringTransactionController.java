package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.RecurringTransactionDTO;
import in.tracking.moneymanager.service.RecurringTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for recurring transaction management.
 * Allows users to set up auto-recurring expenses/incomes (rent, EMI, subscriptions).
 *
 * Endpoints:
 * - GET /api/recurring - Get all active recurring transactions
 * - POST /api/recurring - Create a new recurring transaction
 * - PUT /api/recurring/{id} - Update a recurring transaction
 * - DELETE /api/recurring/{id} - Delete (deactivate) a recurring transaction
 */
@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringTransactionController {

    private final RecurringTransactionService recurringService;

    /**
     * Get all active recurring transactions for current user.
     */
    @GetMapping
    public ResponseEntity<List<RecurringTransactionDTO>> getAllRecurring() {
        return ResponseEntity.ok(recurringService.getAllRecurring());
    }

    /**
     * Create a new recurring transaction.
     *
     * Request body example:
     * {
     *   "name": "House Rent",
     *   "amount": 15000,
     *   "type": "EXPENSE",           // EXPENSE or INCOME
     *   "categoryId": 5,
     *   "icon": "🏠",
     *   "frequency": "MONTHLY",      // DAILY, WEEKLY, MONTHLY, YEARLY
     *   "dayOfPeriod": 1,            // Day of month (1-31) or day of week (1-7)
     *   "startDate": "2026-03-01",   // Optional, defaults to today
     *   "endDate": null,             // Optional, null means no end
     *   "sendReminder": true,        // Send email reminder before due
     *   "reminderDaysBefore": 1      // Days before to send reminder
     * }
     */
    @PostMapping
    public ResponseEntity<RecurringTransactionDTO> createRecurring(
            @RequestBody RecurringTransactionDTO dto) {
        return ResponseEntity.ok(recurringService.createRecurring(dto));
    }

    /**
     * Update an existing recurring transaction.
     */
    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransactionDTO> updateRecurring(
            @PathVariable Long id,
            @RequestBody RecurringTransactionDTO dto) {
        return ResponseEntity.ok(recurringService.updateRecurring(id, dto));
    }

    /**
     * Delete (deactivate) a recurring transaction.
     * The transaction is not physically deleted, just marked inactive.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRecurring(@PathVariable Long id) {
        recurringService.deleteRecurring(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Recurring transaction deleted successfully"
        ));
    }
}


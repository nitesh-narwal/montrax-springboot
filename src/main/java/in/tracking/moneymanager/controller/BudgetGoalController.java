package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.BudgetGoalDTO;
import in.tracking.moneymanager.service.BudgetGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Controller for budget goal management.
 * Allows users to set spending limits per category and track progress.
 *
 * Endpoints:
 * - GET /api/budgets - Get all current month budgets with progress
 * - GET /api/budgets/category/{categoryId} - Get budget for specific category
 * - POST /api/budgets - Create/update a budget goal
 * - DELETE /api/budgets/{id} - Delete a budget goal
 */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetGoalController {

    private final BudgetGoalService budgetGoalService;

    /**
     * Get all budget goals for current month with progress calculations.
     * Returns spent amount, remaining amount, and percentage used.
     */
    @GetMapping
    public ResponseEntity<List<BudgetGoalDTO>> getCurrentMonthBudgets() {
        return ResponseEntity.ok(budgetGoalService.getCurrentMonthBudgets());
    }

    /**
     * Get budget for a specific category.
     * Use categoryId=0 or omit for overall budget.
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<BudgetGoalDTO> getBudgetForCategory(@PathVariable Long categoryId) {
        BudgetGoalDTO budget = budgetGoalService.getBudgetForCategory(
                categoryId == 0 ? null : categoryId);
        if (budget == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(budget);
    }

    /**
     * Get overall budget (not category-specific).
     */
    @GetMapping("/overall")
    public ResponseEntity<BudgetGoalDTO> getOverallBudget() {
        BudgetGoalDTO budget = budgetGoalService.getBudgetForCategory(null);
        if (budget == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(budget);
    }

    /**
     * Create or update a budget goal.
     *
     * Request body:
     * {
     *   "categoryId": 5,        // null or 0 for overall budget
     *   "amount": 10000,        // Budget limit in INR
     *   "alertThreshold": 80,   // Alert when this % is reached (default: 80)
     *   "isRecurring": true     // Copy to next month automatically (default: true)
     * }
     */
    @PostMapping
    public ResponseEntity<BudgetGoalDTO> saveBudgetGoal(@RequestBody Map<String, Object> request) {
        Long categoryId = null;
        if (request.get("categoryId") != null) {
            Long catId = Long.valueOf(request.get("categoryId").toString());
            if (catId != 0) categoryId = catId;
        }

        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        Integer alertThreshold = request.get("alertThreshold") != null
                ? Integer.valueOf(request.get("alertThreshold").toString()) : 80;

        Boolean isRecurring = request.get("isRecurring") != null
                ? Boolean.valueOf(request.get("isRecurring").toString()) : true;

        return ResponseEntity.ok(budgetGoalService.saveBudgetGoal(
                categoryId, amount, alertThreshold, isRecurring));
    }

    /**
     * Delete a budget goal.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBudgetGoal(@PathVariable Long id) {
        budgetGoalService.deleteBudgetGoal(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Budget goal deleted successfully"
        ));
    }
}


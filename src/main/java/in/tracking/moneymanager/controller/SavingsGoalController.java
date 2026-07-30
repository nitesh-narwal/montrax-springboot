package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.SavingsGoalDTO;
import in.tracking.moneymanager.service.SavingsGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Controller for savings goals - tracking progress toward a target amount.
 *
 * Endpoints:
 * - GET /api/savings-goals - list all goals for current user
 * - POST /api/savings-goals - create a new goal
 * - PUT /api/savings-goals/{id} - edit a goal (name/target/icon/date)
 * - POST /api/savings-goals/{id}/contribute - add money toward a goal
 * - DELETE /api/savings-goals/{id} - delete a goal
 */
@RestController
@RequestMapping("/api/savings-goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    @GetMapping
    public ResponseEntity<List<SavingsGoalDTO>> getAllGoals() {
        return ResponseEntity.ok(savingsGoalService.getAllGoals());
    }

    @PostMapping
    public ResponseEntity<SavingsGoalDTO> createGoal(@RequestBody SavingsGoalDTO dto) {
        return ResponseEntity.ok(savingsGoalService.createGoal(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavingsGoalDTO> updateGoal(@PathVariable Long id, @RequestBody SavingsGoalDTO dto) {
        return ResponseEntity.ok(savingsGoalService.updateGoal(id, dto));
    }

    @PostMapping("/{id}/contribute")
    public ResponseEntity<SavingsGoalDTO> addContribution(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ResponseEntity.ok(savingsGoalService.addContribution(id, amount));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteGoal(@PathVariable Long id) {
        savingsGoalService.deleteGoal(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Savings goal deleted successfully"));
    }
}

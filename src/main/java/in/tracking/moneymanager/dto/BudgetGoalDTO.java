package in.tracking.moneymanager.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for budget goal with calculated progress information.
 * Used for displaying budget status to users.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetGoalDTO {

    private Long id;
    private Long categoryId;
    private String categoryName;      // "Food & Dining", "Overall", etc.
    private Integer month;
    private Integer year;
    private BigDecimal budgetAmount;  // Set limit
    private BigDecimal spentAmount;   // How much spent so far
    private BigDecimal remainingAmount; // Budget - Spent
    private Double percentageUsed;    // 0-100+
    private Integer alertThreshold;   // 80 by default
    private Boolean isOverBudget;     // True if spent > budget
    private Boolean isNearLimit;      // True if spent >= alertThreshold%
    private Boolean isRecurring;
}


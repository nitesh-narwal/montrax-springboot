package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for user's budget goals per category.
 * Tracks monthly spending limits and sends alerts when near limit.
 *
 * Table is optimized with composite unique constraint to prevent duplicates.
 */
@Entity
@Table(name = "tbl_budget_goals",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"profile_id", "category_id", "month", "year"},
           name = "uk_budget_profile_category_month"
       ),
       indexes = {
           @Index(name = "idx_budget_profile_month", columnList = "profile_id, month, year")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User who owns this budget goal
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    // Category for this budget (null means overall budget)
    @Column(name = "category_id")
    private Long categoryId;

    // Month (1-12) for this budget goal
    @Column(nullable = false)
    private Integer month;

    // Year for this budget goal
    @Column(nullable = false)
    private Integer year;

    // Budget limit amount in INR
    @Column(name = "budget_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetAmount;

    // Alert threshold percentage (e.g., 80 means alert at 80% spent)
    @Column(name = "alert_threshold")
    @Builder.Default
    private Integer alertThreshold = 80;

    // Whether user has been alerted for this budget period
    @Column(name = "alert_sent")
    @Builder.Default
    private Boolean alertSent = false;

    // Whether this is a recurring monthly goal (copies to next month)
    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}


package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for user's budget goals per category.
 * Tracks monthly spending limits and sends alerts when near limit.
 *
 * Table is optimized with composite unique constraint and proper indexing.
 * Uses JPA relationships instead of storing raw IDs for better ORM benefits.
 */
@Entity
@Table(name = "tbl_budget_goals",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"profile_id", "category_id", "month", "year"},
                name = "uk_budget_profile_category_month"
        ),
        indexes = {
                @Index(name = "idx_budget_profile_month", columnList = "profile_id,month,year"),
                @Index(name = "idx_budget_category_id", columnList = "category_id"),
                @Index(name = "idx_budget_created_at", columnList = "created_at"),
                @Index(name = "idx_budget_status", columnList = "status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "budget_goal_seq")
    @SequenceGenerator(name = "budget_goal_seq", sequenceName = "seq_budget_goals", allocationSize = 1)
    private Long id;

    // User who owns this budget goal - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_budget_profile"))
    private ProfileEntity profile;

    // Category for this budget (null means overall budget)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "category_id", nullable = true, foreignKey = @ForeignKey(name = "fk_budget_category"))
    private CategoryEntity category;

    // Month (1-12) for this budget goal
    @Column(name = "month", nullable = false)
    private Integer month;

    // Year for this budget goal
    @Column(name = "year", nullable = false)
    private Integer year;

    // Budget limit amount
    @Column(name = "budget_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetAmount;

    // Alert threshold percentage (e.g., 80 means alert at 80% spent)
    @Column(name = "alert_threshold", nullable = false)
    @Builder.Default
    private Integer alertThreshold = 80;

    // Whether user has been alerted for this budget period
    @Column(name = "alert_sent", nullable = false)
    @Builder.Default
    private Boolean alertSent = false;

    // Whether this is a recurring monthly goal (copies to next month)
    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private Boolean isRecurring = true;

    // Budget status: ACTIVE, EXCEEDED, COMPLETED
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
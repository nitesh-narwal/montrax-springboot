package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity for user's savings goals (e.g. "Emergency Fund", "New Laptop").
 * Distinct from BudgetGoalEntity: this tracks saving *toward* a target amount
 * rather than a monthly spending cap.
 */
@Entity
@Table(name = "tbl_savings_goals", indexes = {
        @Index(name = "idx_savings_goals_profile_id", columnList = "profile_id"),
        @Index(name = "idx_savings_goals_created_at", columnList = "created_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SavingsGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "savings_goal_seq")
    @SequenceGenerator(name = "savings_goal_seq", sequenceName = "seq_savings_goals", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_savings_goal_profile"))
    private ProfileEntity profile;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(name = "target_date")
    private LocalDate targetDate;

    // ACTIVE or COMPLETED
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

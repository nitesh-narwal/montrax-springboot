package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A personal split-tracking line, not a shared-access record - participants
 * are free text, not linked accounts, since this app has no concept of one
 * profile seeing another profile's data. "I paid X, Raj owes me his share."
 */
@Entity
@Data
@Table(name = "tbl_expense_splits", indexes = {
        @Index(name = "idx_expense_splits_expense_id", columnList = "expense_id")
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpenseSplitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "expense_split_seq")
    @SequenceGenerator(name = "expense_split_seq", sequenceName = "seq_expense_splits", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false, foreignKey = @ForeignKey(name = "fk_split_expense"))
    private ExpenceEntity expense;

    @Column(name = "participant_name", length = 100, nullable = false)
    private String participantName;

    @Column(name = "share_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal shareAmount;

    @Column(name = "is_settled", nullable = false)
    @Builder.Default
    private Boolean isSettled = false;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}

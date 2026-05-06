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

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "tbl_expences", indexes = {
        @Index(name = "idx_expences_profile_id", columnList = "profile_id"),
        @Index(name = "idx_expences_category_id", columnList = "category_id"),
        @Index(name = "idx_expences_date", columnList = "date"),
        @Index(name = "idx_expences_created_at", columnList = "created_at"),
        @Index(name = "idx_expences_profile_date", columnList = "profile_id,date")
})
public class ExpenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "expence_seq")
    @SequenceGenerator(name = "expence_seq", sequenceName = "seq_expences", allocationSize = 1)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "created_at", updatable = false, nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_expence_category"))
    private CategoryEntity category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_expence_profile"))
    private ProfileEntity profile;

    @PrePersist
    public void prePersist() {
        if (this.date == null) {
            this.date = LocalDate.now();
        }
    }
}
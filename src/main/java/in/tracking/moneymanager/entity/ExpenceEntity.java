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
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "tbl_expences", indexes = {
        @Index(name = "idx_expences_profile_id", columnList = "profile_id"),
        @Index(name = "idx_expences_category_id", columnList = "category_id"),
        @Index(name = "idx_expences_account_id", columnList = "account_id"),
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

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

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

    // Optional - not every expense needs to be tied to an account
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = true, foreignKey = @ForeignKey(name = "fk_expence_account"))
    private AccountEntity account;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tbl_expence_tags", joinColumns = @JoinColumn(name = "expence_id"),
            indexes = {
                    @Index(name = "idx_expence_tags_expence_id", columnList = "expence_id"),
                    @Index(name = "idx_expence_tags_tag", columnList = "tag")
            })
    @Column(name = "tag", length = 50)
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (this.date == null) {
            this.date = LocalDate.now();
        }
    }
}
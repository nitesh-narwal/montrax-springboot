package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a bank transaction imported from CSV.
 * Stores raw transaction data for analysis and conversion to expenses/incomes.
 *
 * Supports banks: SBI, HDFC, ICICI, AXIS, KOTAK
 * Type: CREDIT (money in) or DEBIT (money out)
 *
 * This table is optimized for bulk insertions and historical analysis.
 */
@Entity
@Table(name = "tbl_bank_transactions",
        indexes = {
                @Index(name = "idx_bank_tx_profile_id", columnList = "profile_id"),
                @Index(name = "idx_bank_tx_date", columnList = "transaction_date"),
                @Index(name = "idx_bank_tx_type", columnList = "type"),
                @Index(name = "idx_bank_tx_category_id", columnList = "category_id"),
                @Index(name = "idx_bank_tx_is_converted", columnList = "is_converted"),
                @Index(name = "idx_bank_tx_profile_date", columnList = "profile_id,transaction_date"),
                @Index(name = "idx_bank_tx_profile_converted", columnList = "profile_id,is_converted"),
                @Index(name = "idx_bank_tx_created_at", columnList = "created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"profile", "category"})
public class BankTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_tx_seq")
    @SequenceGenerator(name = "bank_tx_seq", sequenceName = "seq_bank_transactions", allocationSize = 1)
    private Long id;

    // User who imported this transaction - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bank_tx_profile"))
    private ProfileEntity profile;

    // Bank name: SBI, HDFC, ICICI, AXIS, KOTAK
    @Column(name = "bank_name", length = 50, nullable = false)
    private String bankName;

    // Transaction date from CSV
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    // Transaction description/narration from bank
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Reference number or cheque number
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    // Transaction amount (always positive, use type to differentiate)
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    // Type: CREDIT (income) or DEBIT (expense)
    @Column(name = "type", length = 10, nullable = false)
    private String type;

    // Account balance after this transaction
    @Column(name = "balance", precision = 15, scale = 2)
    private BigDecimal balance;

    // Category assigned (auto-categorized or manually set) - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "category_id", nullable = true, foreignKey = @ForeignKey(name = "fk_bank_tx_category"))
    private CategoryEntity category;

    // AI-suggested category name (before user confirms)
    @Column(name = "suggested_category", length = 100)
    private String suggestedCategory;

    // Extracted merchant name from description
    @Column(name = "merchant_name", length = 150)
    private String merchantName;

    // Merchant category code for standardization
    @Column(name = "merchant_category", length = 50)
    private String merchantCategory;

    // Whether this has been converted to expense/income
    @Column(name = "is_converted", nullable = false)
    @Builder.Default
    private Boolean isConverted = false;

    // ID of linked expense/income after conversion
    @Column(name = "linked_transaction_id")
    private Long linkedTransactionId;

    // Hash of transaction for duplicate detection (bank + date + amount + ref)
    @Column(name = "transaction_hash", length = 64)
    private String transactionHash;

    // Duplicate detection - whether this is a duplicate import
    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private Boolean isDuplicate = false;

    // Whether transaction has been manually verified by user
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.isDuplicate == null) {
            this.isDuplicate = false;
        }
        if (this.isConverted == null) {
            this.isConverted = false;
        }
        if (this.isVerified == null) {
            this.isVerified = false;
        }
    }
}
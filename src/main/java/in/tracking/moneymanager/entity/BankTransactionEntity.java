package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a bank transaction imported from CSV.
 * Stores raw transaction data for analysis and conversion to expenses/incomes.
 *
 * Supports banks: SBI, HDFC, ICICI, AXIS, KOTAK
 * Type: CREDIT (money in) or DEBIT (money out)
 */
@Entity
@Table(name = "tbl_bank_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User who imported this transaction
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    // Bank name: SBI, HDFC, ICICI, AXIS, KOTAK
    @Column(name = "bank_name")
    private String bankName;

    // Transaction date from CSV
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    // Transaction description/narration from bank
    @Column(columnDefinition = "TEXT")
    private String description;

    // Reference number or cheque number
    @Column(name = "reference_number")
    private String referenceNumber;

    // Transaction amount (always positive, use type to differentiate)
    @Column(nullable = false)
    private BigDecimal amount;

    // Type: CREDIT (income) or DEBIT (expense)
    @Column(nullable = false)
    private String type;

    // Account balance after this transaction
    private BigDecimal balance;

    // Category assigned (auto-categorized or manually set)
    @Column(name = "category_id")
    private Long categoryId;

    // AI-suggested category name (before user confirms)
    @Column(name = "suggested_category")
    private String suggestedCategory;

    // Extracted merchant name from description
    @Column(name = "merchant_name")
    private String merchantName;

    // Whether this has been converted to expense/income
    @Column(name = "is_converted")
    @Builder.Default
    private Boolean isConverted = false;

    // ID of linked expense/income after conversion
    @Column(name = "linked_transaction_id")
    private Long linkedTransactionId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}


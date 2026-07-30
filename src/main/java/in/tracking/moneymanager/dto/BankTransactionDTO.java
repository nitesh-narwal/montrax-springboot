package in.tracking.moneymanager.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for bank transaction data.
 * Used when displaying imported CSV transactions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransactionDTO {

    private Long id;
    private String bankName;
    private LocalDate transactionDate;
    private String description;
    private String referenceNumber;
    private BigDecimal amount;
    private String type;              // CREDIT or DEBIT
    private BigDecimal balance;
    private Long categoryId;
    private String categoryName;
    private String suggestedCategory;
    private String merchantName;
    private Boolean isConverted;
}


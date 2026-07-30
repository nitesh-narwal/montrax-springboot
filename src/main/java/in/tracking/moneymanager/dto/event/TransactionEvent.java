package in.tracking.moneymanager.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionEvent implements Serializable {
    private Long profileId;
    private Long transactionId;
    private String type; // EXPENSE or INCOME
    private String action; // CREATED or DELETED
    private BigDecimal amount;
    private String categoryName;
    private LocalDateTime occurredAt;
}

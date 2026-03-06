package in.tracking.moneymanager.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for recurring transaction.
 * Used for creating, updating, and displaying recurring transactions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTransactionDTO {

    private Long id;
    private String name;
    private BigDecimal amount;
    private String type;           // EXPENSE or INCOME
    private Long categoryId;
    private String categoryName;   // Resolved category name
    private String icon;
    private String frequency;      // DAILY, WEEKLY, MONTHLY, YEARLY
    private Integer dayOfPeriod;   // Day of month (1-31) or day of week (1-7)
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextExecution;
    private LocalDate lastExecuted;
    private Boolean isActive;
    private Boolean sendReminder;
    private Integer reminderDaysBefore;
}


package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity for recurring transactions (rent, EMI, subscriptions).
 * Scheduler creates actual expense/income entries based on these templates.
 *
 * Frequencies: DAILY, WEEKLY, MONTHLY, YEARLY
 */
@Entity
@Table(name = "tbl_recurring_transactions",
       indexes = {
           @Index(name = "idx_recurring_profile", columnList = "profile_id, is_active"),
           @Index(name = "idx_recurring_next_exec", columnList = "next_execution, is_active")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    // Transaction name (e.g., "House Rent", "Netflix Subscription")
    @Column(nullable = false)
    private String name;

    // Transaction amount in INR
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // Type: EXPENSE or INCOME
    @Column(nullable = false, length = 10)
    private String type;

    // Category for the transaction
    @Column(name = "category_id")
    private Long categoryId;

    // Icon for display (emoji or icon name)
    @Column(length = 50)
    private String icon;

    // Frequency: DAILY, WEEKLY, MONTHLY, YEARLY
    @Column(nullable = false, length = 20)
    private String frequency;

    // Day of month for MONTHLY (1-31), day of week for WEEKLY (1=Mon, 7=Sun)
    @Column(name = "day_of_period")
    private Integer dayOfPeriod;

    // Start date for this recurring transaction
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // End date (null means no end - continues indefinitely)
    @Column(name = "end_date")
    private LocalDate endDate;

    // Last date when transaction was auto-created
    @Column(name = "last_executed")
    private LocalDate lastExecuted;

    // Next scheduled execution date
    @Column(name = "next_execution")
    private LocalDate nextExecution;

    // Whether this recurring transaction is active
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // Whether to send reminder email before due date
    @Column(name = "send_reminder")
    @Builder.Default
    private Boolean sendReminder = true;

    // Days before to send reminder (default: 1 day)
    @Column(name = "reminder_days_before")
    @Builder.Default
    private Integer reminderDaysBefore = 1;

    // Whether reminder was sent for current period
    @Column(name = "reminder_sent")
    @Builder.Default
    private Boolean reminderSent = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // CSV of allowed weekdays for WEEKLY frequency (example: "1,3,5")
    @Column(name = "week_days_csv", length = 20)
    private String weekDaysCsv;

    // CSV of weekdays to skip for DAILY frequency (example: "6,7")
    @Column(name = "excluded_week_days_csv", length = 20)
    private String excludedWeekDaysCsv;



}


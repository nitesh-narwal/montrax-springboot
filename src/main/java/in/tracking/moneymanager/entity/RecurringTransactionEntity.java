package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity for recurring transactions (rent, EMI, subscriptions).
 * Scheduler creates actual expense/income entries based on these templates.
 *
 * Frequencies: DAILY, WEEKLY, MONTHLY, YEARLY
 *
 * Optimized for scheduler queries and reminder notifications.
 */
@Entity
@Table(name = "tbl_recurring_transactions",
        indexes = {
                @Index(name = "idx_recurring_profile_active", columnList = "profile_id,is_active"),
                @Index(name = "idx_recurring_next_exec", columnList = "next_execution,is_active"),
                @Index(name = "idx_recurring_category_id", columnList = "category_id"),
                @Index(name = "idx_recurring_frequency", columnList = "frequency"),
                @Index(name = "idx_recurring_created_at", columnList = "created_at"),
                @Index(name = "idx_recurring_type", columnList = "type")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"profile", "category"})
public class RecurringTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recurring_tx_seq")
    @SequenceGenerator(name = "recurring_tx_seq", sequenceName = "seq_recurring_transactions", allocationSize = 1)
    private Long id;

    // User who owns this recurring transaction - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_recurring_profile"))
    private ProfileEntity profile;

    // Transaction name (e.g., "House Rent", "Netflix Subscription")
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    // Transaction amount in INR
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // Type: EXPENSE or INCOME
    @Column(name = "type", length = 10, nullable = false)
    private String type;

    // Category for the transaction - using JPA relationship
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "category_id", nullable = true, foreignKey = @ForeignKey(name = "fk_recurring_category"))
    private CategoryEntity category;

    // Icon for display (emoji or icon name)
    @Column(name = "icon", length = 100)
    private String icon;

    // Frequency: DAILY, WEEKLY, MONTHLY, YEARLY
    @Column(name = "frequency", length = 20, nullable = false)
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
    @Column(name = "next_execution", nullable = false)
    private LocalDate nextExecution;

    // Whether this recurring transaction is active
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Whether to send reminder email before due date
    @Column(name = "send_reminder", nullable = false)
    @Builder.Default
    private Boolean sendReminder = true;

    // Days before to send reminder (default: 1 day)
    @Column(name = "reminder_days_before", nullable = false)
    @Builder.Default
    private Integer reminderDaysBefore = 1;

    // Whether reminder was sent for current period
    @Column(name = "reminder_sent", nullable = false)
    @Builder.Default
    private Boolean reminderSent = false;

    // CSV of allowed weekdays for WEEKLY frequency (example: "1,3,5")
    @Column(name = "week_days_csv", length = 20)
    private String weekDaysCsv;

    // CSV of weekdays to skip for DAILY frequency (example: "6,7")
    @Column(name = "excluded_week_days_csv", length = 20)
    private String excludedWeekDaysCsv;

    // Number of times this transaction has been executed
    @Column(name = "execution_count")
    @Builder.Default
    private Integer executionCount = 0;

    // Last email reminder sent date
    @Column(name = "last_reminder_sent_at")
    private LocalDateTime lastReminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.executionCount == null) {
            this.executionCount = 0;
        }
    }
}
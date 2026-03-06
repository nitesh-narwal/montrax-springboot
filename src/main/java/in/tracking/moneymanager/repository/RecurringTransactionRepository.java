package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.RecurringTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for recurring transaction operations.
 * Optimized for scheduled job queries.
 */
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransactionEntity, Long> {

    // Find all active recurring transactions for a user
    List<RecurringTransactionEntity> findByProfileIdAndIsActiveTrue(Long profileId);

    // Find transactions due for execution today or earlier (batch limited for safety)
    @Query("SELECT r FROM RecurringTransactionEntity r WHERE r.isActive = true " +
           "AND r.nextExecution <= :date ORDER BY r.nextExecution ASC")
    List<RecurringTransactionEntity> findDueTransactions(@Param("date") LocalDate date);

    // Find transactions due for reminder (next execution in X days)
    @Query("SELECT r FROM RecurringTransactionEntity r WHERE r.isActive = true " +
           "AND r.sendReminder = true AND r.reminderSent = false " +
           "AND r.nextExecution BETWEEN :today AND :reminderDate")
    List<RecurringTransactionEntity> findTransactionsNeedingReminder(
            @Param("today") LocalDate today,
            @Param("reminderDate") LocalDate reminderDate);

    // Reset reminder flags for next period (after execution)
    @Modifying
    @Query("UPDATE RecurringTransactionEntity r SET r.reminderSent = false " +
           "WHERE r.id IN :ids")
    void resetReminderFlags(@Param("ids") List<Long> ids);

    // Count active recurring for a profile (for limits)
    long countByProfileIdAndIsActiveTrue(Long profileId);

    // Delete all recurring transactions for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);
}


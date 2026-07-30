package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.BankTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for bank transaction operations.
 * Handles imported CSV transactions.
 */
public interface BankTransactionRepository extends JpaRepository<BankTransactionEntity, Long> {

    // Get transactions for a user with pagination, most recent first
    Page<BankTransactionEntity> findByProfileIdOrderByTransactionDateDesc(Long profileId, Pageable pageable);

    // Get transactions in a date range for analysis
    Page<BankTransactionEntity> findByProfileIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            Long profileId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // Get transactions that haven't been converted to expense/income yet
    List<BankTransactionEntity> findByProfileIdAndIsConvertedFalse(Long profileId);

    // Count imports this month for rate limiting
    Long countByProfileIdAndCreatedAtAfter(Long profileId, LocalDateTime after);

    // Delete all bank transactions for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);

    // Delete old bank transactions for data retention (used by scheduled cleanup)
    void deleteByProfileIdAndTransactionDateBefore(Long profileId, LocalDate date);

    // Count old bank transactions for a profile (for logging/reporting)
    long countByProfileIdAndTransactionDateBefore(Long profileId, LocalDate date);
}


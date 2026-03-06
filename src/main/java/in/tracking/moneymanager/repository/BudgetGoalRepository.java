package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.BudgetGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for budget goal operations.
 * Optimized queries for Neon free tier (limited compute).
 */
public interface BudgetGoalRepository extends JpaRepository<BudgetGoalEntity, Long> {

    // Find all budget goals for a user for a specific month
    List<BudgetGoalEntity> findByProfileIdAndMonthAndYear(Long profileId, Integer month, Integer year);

    // Find specific category budget for a month
    Optional<BudgetGoalEntity> findByProfileIdAndCategoryIdAndMonthAndYear(
            Long profileId, Long categoryId, Integer month, Integer year);

    // Find overall budget (categoryId is null) for a month
    Optional<BudgetGoalEntity> findByProfileIdAndCategoryIdIsNullAndMonthAndYear(
            Long profileId, Integer month, Integer year);

    // Find all recurring budgets for a profile (to copy to new month)
    List<BudgetGoalEntity> findByProfileIdAndIsRecurringTrue(Long profileId);

    // Find budgets that need alert (near limit, not yet alerted)
    @Query("SELECT b FROM BudgetGoalEntity b WHERE b.month = :month AND b.year = :year AND b.alertSent = false")
    List<BudgetGoalEntity> findBudgetsNeedingAlert(@Param("month") Integer month, @Param("year") Integer year);

    // Delete old budgets (more than 1 year old) - for cleanup job
    @Query("DELETE FROM BudgetGoalEntity b WHERE b.year < :year")
    void deleteOldBudgets(@Param("year") Integer year);

    // Delete all budgets for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);
}


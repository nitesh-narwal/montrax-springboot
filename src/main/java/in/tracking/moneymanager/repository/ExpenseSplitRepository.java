package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.ExpenseSplitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplitEntity, Long> {

    List<ExpenseSplitEntity> findByExpenseId(Long expenseId);

    List<ExpenseSplitEntity> findByExpenseIdIn(List<Long> expenseIds);

    void deleteByExpenseId(Long expenseId);

    // "Who owes you" - unsettled splits grouped by participant, across the current profile's expenses
    @Query("""
       SELECT s.participantName, COALESCE(SUM(s.shareAmount), 0)
       FROM ExpenseSplitEntity s
       WHERE s.expense.profile.id = :profileId AND s.isSettled = false
       GROUP BY s.participantName
       ORDER BY SUM(s.shareAmount) DESC
       """)
    List<Object[]> sumUnsettledByParticipant(@Param("profileId") Long profileId);
}

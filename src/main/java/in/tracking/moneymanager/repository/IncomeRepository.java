package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.IncomeEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<IncomeEntity, Long> {

    //select * from tbl_income where profile_id = ?1 order by date desc
    List<IncomeEntity> findByProfileIdOrderByDateDesc(Long profileId);

    //select * from tbl_income where profile_id = ?1 order by date desc limit 5
    List<IncomeEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);

    @Query("SELECT SUM(e.amount) FROM IncomeEntity e WHERE e.profile.id = :profileId")
    BigDecimal findTotalIncomeByProfileId(@Param("profileId") Long profileId);

    //select * from tbl_income where profile_id = ?1 and date between ?2 and ?3 and name like ?4
    List<IncomeEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(
            Long profileId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            Sort sort
    );

    //select * from tbl_income where profile_id = ?1 and date between ?2 and ?3
    List<IncomeEntity> findByProfileIdAndDateBetween(
            Long profileId,
            LocalDate startDate,
            LocalDate endDate
    );

    // Delete all incomes for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);

    // Delete old incomes for data retention (used by scheduled cleanup)
    void deleteByProfileIdAndDateBefore(Long profileId, LocalDate date);

    // Count old incomes for a profile (for logging/reporting)
    long countByProfileIdAndDateBefore(Long profileId, LocalDate date);

    @Query("""
       SELECT COALESCE(SUM(i.amount), 0)
       FROM IncomeEntity i
       WHERE i.profile.id = :profileId
         AND i.date BETWEEN :startDate AND :endDate
       """)
    BigDecimal findTotalIncomeByProfileIdAndDateBetween(
            @Param("profileId") Long profileId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

}

package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.ExpenceEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenceRepository extends JpaRepository<ExpenceEntity, Long> {

    //select * from tbl_expence where profile_id = ?1 order by date desc
    List<ExpenceEntity> findByProfileIdOrderByDateDesc(Long profileId);

    //select * from tbl_expence where profile_id = ?1 order by date desc limit 5
    List<ExpenceEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);

    @Query("SELECT SUM(e.amount) FROM ExpenceEntity e WHERE e.profile.id = :profileId")
    BigDecimal findTotalExpenceByProfileId(@Param("profileId") Long profileId);

    //select * from tbl_expence where profile_id = ?1 and date between ?2 and ?3 and name like ?4
    List<ExpenceEntity> findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(
            Long profileId,
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            Sort sort
    );

    //select * from tbl_expence where profile_id = ?1 and date between ?2 and ?3
    List<ExpenceEntity> findByProfileIdAndDateBetween(
            Long profileId,
            LocalDate startDate,
            LocalDate endDate
    );

    //select * from tbl_expence where profile_id = ?1 and date = ?2
    List<ExpenceEntity> findByProfileIdAndDate(Long profileId, LocalDate date);

    //select * from tbl_expence where profile_id = ?1 and category_id = ?2 and date between ?3 and ?4
    List<ExpenceEntity> findByProfileIdAndCategoryIdAndDateBetween(
            Long profileId,
            Long categoryId,
            LocalDate startDate,
            LocalDate endDate
    );

    // Delete all expenses for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);

    // Delete old expenses for data retention (used by scheduled cleanup)
    void deleteByProfileIdAndDateBefore(Long profileId, LocalDate date);

    // Count old expenses for a profile (for logging/reporting)
    long countByProfileIdAndDateBefore(Long profileId, LocalDate date);

    @Query("""
       SELECT COALESCE(SUM(e.amount), 0)
       FROM ExpenceEntity e
       WHERE e.profile.id = :profileId
         AND e.date BETWEEN :startDate AND :endDate
       """)
    BigDecimal findTotalExpenceByProfileIdAndDateBetween(
            @Param("profileId") Long profileId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

}

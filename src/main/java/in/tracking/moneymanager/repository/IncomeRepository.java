package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.IncomeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Paginated transaction history
    Page<IncomeEntity> findByProfileId(Long profileId, Pageable pageable);

    //select * from tbl_income where profile_id = ?1 order by date desc limit 5
    List<IncomeEntity> findTop5ByProfileIdOrderByDateDesc(Long profileId);

    @Query("SELECT SUM(e.amount) FROM IncomeEntity e WHERE e.profile.id = :profileId")
    BigDecimal findTotalIncomeByProfileId(@Param("profileId") Long profileId);

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

    // Same filter as findByProfileIdAndDateBetweenAndNameContainingIgnoreCase, plus an optional tag
    // (tag = null means "no tag filter", so this can replace the plain search unconditionally)
    @Query("""
       SELECT DISTINCT i FROM IncomeEntity i LEFT JOIN i.tags t
       WHERE i.profile.id = :profileId
         AND i.date BETWEEN :startDate AND :endDate
         AND LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
         AND (:tag IS NULL OR t = :tag)
       """)
    List<IncomeEntity> searchByProfileAndFilters(
            @Param("profileId") Long profileId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword,
            @Param("tag") String tag,
            Sort sort);

    @Query("""
       SELECT DISTINCT i FROM IncomeEntity i LEFT JOIN i.tags t
       WHERE i.profile.id = :profileId
         AND i.date BETWEEN :startDate AND :endDate
         AND LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
         AND (:tag IS NULL OR t = :tag)
       """)
    Page<IncomeEntity> searchByProfileAndFilters(
            @Param("profileId") Long profileId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword,
            @Param("tag") String tag,
            Pageable pageable);

}

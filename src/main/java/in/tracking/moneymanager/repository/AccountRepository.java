package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    List<AccountEntity> findByProfileId(Long profileId);

    Optional<AccountEntity> findByIdAndProfileId(Long id, Long profileId);

    Boolean existsByNameAndProfileId(String name, Long profileId);

    void deleteByProfileId(Long profileId);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM AccountEntity a WHERE a.profile.id = :profileId AND a.isActive = true")
    BigDecimal sumBalanceByProfileId(@Param("profileId") Long profileId);
}

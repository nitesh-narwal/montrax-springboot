package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.entity.SavingsGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoalEntity, Long> {

    List<SavingsGoalEntity> findByProfileOrderByCreatedAtDesc(ProfileEntity profile);

    Optional<SavingsGoalEntity> findByIdAndProfile(Long id, ProfileEntity profile);
}

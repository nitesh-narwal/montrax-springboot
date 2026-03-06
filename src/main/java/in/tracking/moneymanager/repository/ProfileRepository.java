package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.ProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<ProfileEntity, Long> {

    Optional<ProfileEntity> findByEmail(String email);

    Optional<ProfileEntity> findByActivationToken(String activationToken);

    Optional<ProfileEntity> findByPasswordResetToken(String passwordResetToken);

    // Find profiles that are pending deletion and past their scheduled deletion time
    List<ProfileEntity> findByIsPendingDeletionTrueAndDeletionScheduledAtBefore(LocalDateTime dateTime);
}

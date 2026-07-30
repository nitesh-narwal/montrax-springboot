package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.ProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<ProfileEntity, Long> {

    Optional<ProfileEntity> findByEmail(String email);

    Optional<ProfileEntity> findByActivationToken(String activationToken);

    Optional<ProfileEntity> findByPasswordResetToken(String passwordResetToken);

    // phone_number has no DB-level uniqueness (unlike email) - can match multiple rows
    List<ProfileEntity> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    // Find profiles that are pending deletion and past their scheduled deletion time
    List<ProfileEntity> findByIsPendingDeletionTrueAndDeletionScheduledAtBefore(LocalDateTime dateTime);

    // Admin panel: paginated + filtered user listing
    Page<ProfileEntity> findByRole(String role, Pageable pageable);

    Page<ProfileEntity> findByFullnameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullname, String email, Pageable pageable);

    long countByRole(String role);

    long countByIsActiveTrue();

    long countByIsPhoneVerifiedTrue();
}

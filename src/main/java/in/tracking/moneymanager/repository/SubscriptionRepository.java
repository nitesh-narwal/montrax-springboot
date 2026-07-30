package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for user subscription operations.
 * Handles subscription status checks and expiry management.
 */
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    // Find active subscription for a user (only one can be active at a time)
    Optional<SubscriptionEntity> findByProfileIdAndStatus(Long profileId, String status);

    // Find all subscriptions expiring on a specific date (for sending reminders)
    List<SubscriptionEntity> findByEndDateAndStatus(LocalDate endDate, String status);

    // Find subscriptions in grace period that need to be expired
    List<SubscriptionEntity> findByStatusAndEndDateBefore(String status, LocalDate date);

    // Find latest subscription for a user regardless of status
    Optional<SubscriptionEntity> findFirstByProfileIdOrderByCreatedAtDesc(Long profileId);

    // Delete all subscriptions for a profile (used for account deletion)
    void deleteByProfileId(Long profileId);
}


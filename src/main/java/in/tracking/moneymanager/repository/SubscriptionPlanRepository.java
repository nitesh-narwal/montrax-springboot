package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for subscription plan operations.
 * Provides methods to retrieve available plans.
 */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, Long> {

    // Find all active plans for display to users
    List<SubscriptionPlanEntity> findByIsActiveTrue();

    // Find plan by name (FREE, BASIC, PREMIUM)
    Optional<SubscriptionPlanEntity> findByName(String name);
}


package in.tracking.moneymanager.repository;

import in.tracking.moneymanager.entity.NetWorthSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshotEntity, Long> {

    List<NetWorthSnapshotEntity> findByProfileIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long profileId, LocalDate startDate, LocalDate endDate);

    Optional<NetWorthSnapshotEntity> findByProfileIdAndSnapshotDate(Long profileId, LocalDate snapshotDate);

    void deleteByProfileId(Long profileId);
}

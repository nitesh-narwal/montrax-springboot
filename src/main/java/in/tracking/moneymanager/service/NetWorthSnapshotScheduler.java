package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.NetWorthSnapshotEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.AccountRepository;
import in.tracking.moneymanager.repository.NetWorthSnapshotRepository;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Snapshots each profile's net worth (sum of active account balances) once a
 * day so AccountController can chart a trend over time. Same cron/per-profile
 * try-catch shape as DataRetentionService/AccountDeletionScheduler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetWorthSnapshotScheduler {

    private final ProfileRepository profileRepository;
    private final AccountRepository accountRepository;
    private final NetWorthSnapshotRepository netWorthSnapshotRepository;

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void snapshotNetWorth() {
        LocalDate today = LocalDate.now();
        List<ProfileEntity> profiles = profileRepository.findAll();
        int snapshotted = 0;

        for (ProfileEntity profile : profiles) {
            try {
                BigDecimal netWorth = accountRepository.sumBalanceByProfileId(profile.getId());
                if (netWorth == null) {
                    continue; // no accounts for this profile - nothing to snapshot
                }
                netWorthSnapshotRepository.findByProfileIdAndSnapshotDate(profile.getId(), today)
                        .ifPresentOrElse(
                                existing -> existing.setTotalNetWorth(netWorth),
                                () -> netWorthSnapshotRepository.save(NetWorthSnapshotEntity.builder()
                                        .profileId(profile.getId())
                                        .snapshotDate(today)
                                        .totalNetWorth(netWorth)
                                        .build()));
                snapshotted++;
            } catch (Exception e) {
                log.error("Failed to snapshot net worth for profile {}: {}", profile.getId(), e.getMessage());
            }
        }
        log.info("Net worth snapshot job complete. Snapshotted {} profiles", snapshotted);
    }
}

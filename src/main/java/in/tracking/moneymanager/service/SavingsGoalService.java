package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.SavingsGoalDTO;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.entity.SavingsGoalEntity;
import in.tracking.moneymanager.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing savings goals.
 * Tracks progress toward a target amount, separate from budget spending caps.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final ProfileService profileService;

    private static final int MAX_GOALS_PER_USER = 30;

    public List<SavingsGoalDTO> getAllGoals() {
        ProfileEntity profile = profileService.getCurrentProfile();
        return savingsGoalRepository.findByProfileOrderByCreatedAtDesc(profile)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SavingsGoalDTO createGoal(SavingsGoalDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();

        long count = savingsGoalRepository.findByProfileOrderByCreatedAtDesc(profile).size();
        if (count >= MAX_GOALS_PER_USER) {
            throw new RuntimeException("Maximum savings goals limit reached (" + MAX_GOALS_PER_USER + ")");
        }

        validateGoalInput(dto);

        SavingsGoalEntity entity = SavingsGoalEntity.builder()
                .profile(profile)
                .name(dto.getName())
                .icon(dto.getIcon() != null ? dto.getIcon() : "🎯")
                .targetAmount(dto.getTargetAmount())
                .currentAmount(BigDecimal.ZERO)
                .targetDate(dto.getTargetDate())
                .status("ACTIVE")
                .build();

        SavingsGoalEntity saved = savingsGoalRepository.save(entity);
        log.info("Created savings goal: {} for profile {}", dto.getName(), profile.getId());
        return toDTO(saved);
    }

    @Transactional
    public SavingsGoalDTO updateGoal(Long id, SavingsGoalDTO dto) {
        SavingsGoalEntity entity = getOwnedGoal(id);

        validateGoalInput(dto);

        entity.setName(dto.getName());
        entity.setIcon(dto.getIcon() != null ? dto.getIcon() : entity.getIcon());
        entity.setTargetAmount(dto.getTargetAmount());
        entity.setTargetDate(dto.getTargetDate());
        refreshStatus(entity);

        SavingsGoalEntity saved = savingsGoalRepository.save(entity);
        log.info("Updated savings goal: {} (profile: {})", id, entity.getProfile().getId());
        return toDTO(saved);
    }

    @Transactional
    public SavingsGoalDTO addContribution(Long id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Contribution amount must be greater than zero");
        }

        SavingsGoalEntity entity = getOwnedGoal(id);
        entity.setCurrentAmount(entity.getCurrentAmount().add(amount));
        refreshStatus(entity);

        SavingsGoalEntity saved = savingsGoalRepository.save(entity);
        log.info("Added ₹{} to savings goal {} (profile: {})", amount, id, entity.getProfile().getId());
        return toDTO(saved);
    }

    @Transactional
    public void deleteGoal(Long id) {
        SavingsGoalEntity entity = getOwnedGoal(id);
        savingsGoalRepository.delete(entity);
        log.info("Deleted savings goal: {} (profile: {})", id, entity.getProfile().getId());
    }

    private SavingsGoalEntity getOwnedGoal(Long id) {
        ProfileEntity profile = profileService.getCurrentProfile();
        return savingsGoalRepository.findByIdAndProfile(id, profile)
                .orElseThrow(() -> new RuntimeException("Savings goal not found"));
    }

    private void refreshStatus(SavingsGoalEntity entity) {
        boolean completed = entity.getCurrentAmount().compareTo(entity.getTargetAmount()) >= 0;
        entity.setStatus(completed ? "COMPLETED" : "ACTIVE");
    }

    private void validateGoalInput(SavingsGoalDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("Goal name is required");
        }
        if (dto.getTargetAmount() == null || dto.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Target amount must be greater than zero");
        }
        if (dto.getTargetDate() != null && dto.getTargetDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Target date cannot be in the past");
        }
    }

    private SavingsGoalDTO toDTO(SavingsGoalEntity entity) {
        BigDecimal remaining = entity.getTargetAmount().subtract(entity.getCurrentAmount());
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        Double percentage = entity.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? entity.getCurrentAmount().multiply(BigDecimal.valueOf(100))
                        .divide(entity.getTargetAmount(), 2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        return SavingsGoalDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .targetAmount(entity.getTargetAmount())
                .currentAmount(entity.getCurrentAmount())
                .remainingAmount(remaining)
                .percentageProgress(Math.min(percentage, 100.0))
                .targetDate(entity.getTargetDate())
                .status(entity.getStatus())
                .isCompleted("COMPLETED".equals(entity.getStatus()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

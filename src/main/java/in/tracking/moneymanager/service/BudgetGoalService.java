package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.BudgetGoalDTO;
import in.tracking.moneymanager.entity.BudgetGoalEntity;
import in.tracking.moneymanager.entity.CategoryEntity;
import in.tracking.moneymanager.entity.ExpenceEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.BudgetGoalRepository;
import in.tracking.moneymanager.repository.CategoryRepository;
import in.tracking.moneymanager.repository.ExpenceRepository;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing budget goals.
 * Tracks spending against budgets and sends alerts when near limit.
 *
 * Optimized for Neon free tier with efficient queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetGoalService {

    private final BudgetGoalRepository budgetGoalRepository;
    private final ExpenceRepository expenceRepository;
    private final CategoryRepository categoryRepository;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final EmailService emailService;


    /**
     * Get all budget goals for current month with progress calculations.
     */
    @Transactional(readOnly = false)
    public List<BudgetGoalDTO> getCurrentMonthBudgets() {
        Long profileId = profileService.getCurrentProfile().getId();
        YearMonth current = YearMonth.now();

        // Ensure current month has recurring budget entries before reading.
        initializeCurrentMonthRecurringBudgets(profileId, current);

        List<BudgetGoalEntity> budgets = budgetGoalRepository
                .findByProfileIdAndMonthAndYear(profileId, current.getMonthValue(), current.getYear());

        return budgets.stream()
                .map(this::toBudgetGoalDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get budget for a specific category (or overall if categoryId is null).
     */
    public BudgetGoalDTO getBudgetForCategory(Long categoryId) {
        Long profileId = profileService.getCurrentProfile().getId();
        YearMonth current = YearMonth.now();

        Optional<BudgetGoalEntity> budget = categoryId != null
                ? budgetGoalRepository.findByProfileIdAndCategoryIdAndMonthAndYear(
                        profileId, categoryId, current.getMonthValue(), current.getYear())
                : budgetGoalRepository.findByProfileIdAndCategoryIdIsNullAndMonthAndYear(
                        profileId, current.getMonthValue(), current.getYear());

        return budget.map(this::toBudgetGoalDTO).orElse(null);
    }

    /**
     * Create or update a budget goal.
     */
    @Transactional
    public BudgetGoalDTO saveBudgetGoal(Long categoryId, BigDecimal amount,
                                         Integer alertThreshold, Boolean isRecurring) {
        Long profileId = profileService.getCurrentProfile().getId();
        YearMonth current = YearMonth.now();

        // Find existing or create new
        Optional<BudgetGoalEntity> existing = categoryId != null
                ? budgetGoalRepository.findByProfileIdAndCategoryIdAndMonthAndYear(
                        profileId, categoryId, current.getMonthValue(), current.getYear())
                : budgetGoalRepository.findByProfileIdAndCategoryIdIsNullAndMonthAndYear(
                        profileId, current.getMonthValue(), current.getYear());

        BudgetGoalEntity budget = existing.orElse(BudgetGoalEntity.builder()
                .profileId(profileId)
                .categoryId(categoryId)
                .month(current.getMonthValue())
                .year(current.getYear())
                .build());

        budget.setBudgetAmount(amount);
        budget.setAlertThreshold(alertThreshold != null ? alertThreshold : 80);
        budget.setIsRecurring(isRecurring != null ? isRecurring : true);
        budget.setAlertSent(false);  // Reset alert when budget changes

        BudgetGoalEntity saved = budgetGoalRepository.save(budget);
        log.info("Saved budget goal: ₹{} for category {} (profile: {})",
                amount, categoryId, profileId);

        return toBudgetGoalDTO(saved);
    }

    /**
     * Delete a budget goal.
     */
    @Transactional
    public void deleteBudgetGoal(Long budgetId) {
        Long profileId = profileService.getCurrentProfile().getId();
        BudgetGoalEntity budget = budgetGoalRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget goal not found"));

        if (!budget.getProfileId().equals(profileId)) {
            throw new RuntimeException("Access denied");
        }

        budgetGoalRepository.delete(budget);
        log.info("Deleted budget goal: {} (profile: {})", budgetId, profileId);
    }

    /**
     * Cleanup job: removes budget rows older than retention window.
     * Runs once a month on day 1 at 2:30 AM IST.
     */
    @Scheduled(cron = "0 30 2 1 * *", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupOldBudgets() {
        int deleted = cleanupOldBudgetsNow();
        log.info("Budget cleanup complete. Deleted {} rows", deleted);
    }

    @Transactional
    public int cleanupOldBudgetsNow() {
        int cutoffYear = Year.now().minusYears(1).getValue();
        int deleted = budgetGoalRepository.deleteOldBudgets(cutoffYear);
        log.info("Deleted {} budget rows older than year {}", deleted, cutoffYear);
        return deleted;
    }
    /**
     * Check budget status after adding an expense.
     * Returns warning message if near or over budget.
     * Called from ExpenceService after adding expense.
     */
    public String checkBudgetStatus(Long categoryId, Long profileId) {
        YearMonth current = YearMonth.now();

        // Check category-specific budget
        Optional<BudgetGoalEntity> categoryBudget = budgetGoalRepository
                .findByProfileIdAndCategoryIdAndMonthAndYear(
                        profileId, categoryId, current.getMonthValue(), current.getYear());

        if (categoryBudget.isPresent()) {
            BudgetGoalDTO dto = toBudgetGoalDTOForProfile(categoryBudget.get(), profileId);
            if (dto.getIsOverBudget()) {
                return "⚠️ You've exceeded your " + dto.getCategoryName() + " budget!";
            } else if (dto.getIsNearLimit()) {
                return "⚠️ You're at " + dto.getPercentageUsed().intValue() +
                       "% of your " + dto.getCategoryName() + " budget";
            }
        }

        // Check overall budget
        Optional<BudgetGoalEntity> overallBudget = budgetGoalRepository
                .findByProfileIdAndCategoryIdIsNullAndMonthAndYear(
                        profileId, current.getMonthValue(), current.getYear());

        if (overallBudget.isPresent()) {
            BudgetGoalDTO dto = toBudgetGoalDTOForProfile(overallBudget.get(), profileId);
            if (dto.getIsOverBudget()) {
                return "⚠️ You've exceeded your overall monthly budget!";
            } else if (dto.getIsNearLimit()) {
                return "⚠️ You're at " + dto.getPercentageUsed().intValue() +
                       "% of your overall monthly budget";
            }
        }

        return null;  // No warning
    }

    /**
     * Scheduled job: Copy recurring budgets to new month.
     * Runs on 1st of every month at 00:05 AM IST.
     */
    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Kolkata")
    @Transactional
    public void copyRecurringBudgetsToNewMonth() {
        log.info("Running monthly budget copy job...");
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);

        // Get all budgets from previous month that are recurring
        List<BudgetGoalEntity> previousBudgets = budgetGoalRepository
                .findBudgetsNeedingAlert(previous.getMonthValue(), previous.getYear())
                .stream()
                .filter(BudgetGoalEntity::getIsRecurring)
                .collect(Collectors.toList());

        int copied = 0;
        for (BudgetGoalEntity old : previousBudgets) {
            // Check if budget already exists for new month
            Optional<BudgetGoalEntity> existing = old.getCategoryId() != null
                    ? budgetGoalRepository.findByProfileIdAndCategoryIdAndMonthAndYear(
                            old.getProfileId(), old.getCategoryId(),
                            current.getMonthValue(), current.getYear())
                    : budgetGoalRepository.findByProfileIdAndCategoryIdIsNullAndMonthAndYear(
                            old.getProfileId(), current.getMonthValue(), current.getYear());

            if (existing.isEmpty()) {
                BudgetGoalEntity newBudget = BudgetGoalEntity.builder()
                        .profileId(old.getProfileId())
                        .categoryId(old.getCategoryId())
                        .month(current.getMonthValue())
                        .year(current.getYear())
                        .budgetAmount(old.getBudgetAmount())
                        .alertThreshold(old.getAlertThreshold())
                        .isRecurring(true)
                        .alertSent(false)
                        .build();
                budgetGoalRepository.save(newBudget);
                copied++;
            }
        }

        log.info("Budget copy job complete. Copied {} budgets to new month", copied);
    }

    /**
     * Scheduled job: Send alerts for budgets near limit.
     * Runs every day at 9 PM IST.
     */
    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendBudgetAlerts() {
        log.info("Running budget alert job...");
        YearMonth current = YearMonth.now();

        List<BudgetGoalEntity> budgets = budgetGoalRepository
                .findBudgetsNeedingAlert(current.getMonthValue(), current.getYear());

        int alertsSent = 0;
        for (BudgetGoalEntity budget : budgets) {
            try {
                BudgetGoalDTO dto = toBudgetGoalDTOForProfile(budget, budget.getProfileId());

                if (dto.getIsNearLimit() || dto.getIsOverBudget()) {
                    // Get profile email
                    ProfileEntity profile = profileRepository.findById(budget.getProfileId())
                            .orElse(null);

                    if (profile != null && profile.getEmail() != null) {
                        // Send email alert
                        String subject = dto.getIsOverBudget()
                                ? "🚨 Budget Exceeded: " + dto.getCategoryName()
                                : "⚠️ Budget Alert: " + dto.getCategoryName();

                        String body = buildBudgetAlertEmail(dto, profile.getFullname());
                        emailService.sendEmail(profile.getEmail(), subject, body);

                        budget.setAlertSent(true);
                        budgetGoalRepository.save(budget);
                        alertsSent++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send budget alert for budget {}: {}",
                        budget.getId(), e.getMessage());
            }
        }

        log.info("Budget alert job complete. Sent {} alerts", alertsSent);
    }

    /**
     * Auto-seeds current month budgets from user's recurring templates when missing.
     * This keeps UX smooth even if monthly scheduler was skipped.
     */
    protected void initializeCurrentMonthRecurringBudgets(Long profileId, YearMonth current) {
        List<BudgetGoalEntity> currentMonthBudgets = budgetGoalRepository
                .findByProfileIdAndMonthAndYear(profileId, current.getMonthValue(), current.getYear());

        // Already initialized for this month
        if (!currentMonthBudgets.isEmpty()) {
            return;
        }

        List<BudgetGoalEntity> recurringBudgets = budgetGoalRepository
                .findByProfileIdAndIsRecurringTrue(profileId);

        if (recurringBudgets.isEmpty()) {
            return;
        }

        // Pick latest recurring budget per category (null category = overall budget)
        Map<Long, BudgetGoalEntity> latestByCategory = new java.util.HashMap<>();
        for (BudgetGoalEntity b : recurringBudgets) {
            Long key = b.getCategoryId(); // null means overall budget
            BudgetGoalEntity existing = latestByCategory.get(key);

            if (existing == null || isNewerBudget(b, existing)) {
                latestByCategory.put(key, b);
            }
        }

        for (BudgetGoalEntity template : latestByCategory.values()) {
            BudgetGoalEntity newBudget = BudgetGoalEntity.builder()
                    .profileId(profileId)
                    .categoryId(template.getCategoryId())
                    .month(current.getMonthValue())
                    .year(current.getYear())
                    .budgetAmount(template.getBudgetAmount())
                    .alertThreshold(template.getAlertThreshold())
                    .isRecurring(true)
                    .alertSent(false)
                    .build();

            budgetGoalRepository.save(newBudget);
        }

        log.info("Initialized {} recurring budgets for profile {} for {}/{}",
                latestByCategory.size(), profileId, current.getMonthValue(), current.getYear());
    }

    /** Compares budget period recency using year/month. */
    private boolean isNewerBudget(BudgetGoalEntity candidate, BudgetGoalEntity base) {
        if (!candidate.getYear().equals(base.getYear())) {
            return candidate.getYear() > base.getYear();
        }
        return candidate.getMonth() > base.getMonth();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Convert entity to DTO with calculated progress for current user.
     */
    private BudgetGoalDTO toBudgetGoalDTO(BudgetGoalEntity entity) {
        return toBudgetGoalDTOForProfile(entity, entity.getProfileId());
    }

    /**
     * Convert entity to DTO with calculated progress for specific profile.
     */
    private BudgetGoalDTO toBudgetGoalDTOForProfile(BudgetGoalEntity entity, Long profileId) {
        // Calculate spent amount for this budget period
        LocalDate startOfMonth = LocalDate.of(entity.getYear(), entity.getMonth(), 1);
        LocalDate endOfMonth = YearMonth.of(entity.getYear(), entity.getMonth()).atEndOfMonth();

        BigDecimal spentAmount;
        String categoryName;

        if (entity.getCategoryId() != null) {
            // Category-specific budget
            spentAmount = expenceRepository
                    .findByProfileIdAndCategoryIdAndDateBetween(
                            profileId, entity.getCategoryId(), startOfMonth, endOfMonth)
                    .stream()
                    .map(ExpenceEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            categoryName = categoryRepository.findById(entity.getCategoryId())
                    .map(CategoryEntity::getName)
                    .orElse("Unknown");
        } else {
            // Overall budget
            spentAmount = expenceRepository
                    .findByProfileIdAndDateBetween(profileId, startOfMonth, endOfMonth)
                    .stream()
                    .map(ExpenceEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            categoryName = "Overall";
        }

        BigDecimal remainingAmount = entity.getBudgetAmount().subtract(spentAmount);
        Double percentageUsed = entity.getBudgetAmount().compareTo(BigDecimal.ZERO) > 0
                ? spentAmount.multiply(BigDecimal.valueOf(100))
                        .divide(entity.getBudgetAmount(), 2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        return BudgetGoalDTO.builder()
                .id(entity.getId())
                .categoryId(entity.getCategoryId())
                .categoryName(categoryName)
                .month(entity.getMonth())
                .year(entity.getYear())
                .budgetAmount(entity.getBudgetAmount())
                .spentAmount(spentAmount)
                .remainingAmount(remainingAmount)
                .percentageUsed(percentageUsed)
                .alertThreshold(entity.getAlertThreshold())
                .isOverBudget(percentageUsed > 100)
                .isNearLimit(percentageUsed >= entity.getAlertThreshold())
                .isRecurring(entity.getIsRecurring())
                .build();
    }

    /**
     * Build HTML email body for budget alert.
     */
    private String buildBudgetAlertEmail(BudgetGoalDTO dto, String userName) {
        String status = dto.getIsOverBudget() ? "exceeded" : "approaching the limit of";
        String color = dto.getIsOverBudget() ? "#dc3545" : "#ffc107";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2 style="color: %s;">Budget Alert</h2>
                <p>Hi %s,</p>
                <p>Your <strong>%s</strong> budget has %s your set limit.</p>
                <table style="border-collapse: collapse; margin: 20px 0;">
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Budget:</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">₹%s</td></tr>
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Spent:</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">₹%s</td></tr>
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Remaining:</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">₹%s</td></tr>
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Usage:</td>
                        <td style="padding: 8px; border: 1px solid #ddd; color: %s;">%s%%</td></tr>
                </table>
                <p>Log in to your Money Manager account to review your spending.</p>
                <p>Best regards,<br>Money Manager Team</p>
            </body>
            </html>
            """,
            color, userName, dto.getCategoryName(), status,
            dto.getBudgetAmount(), dto.getSpentAmount(), dto.getRemainingAmount(),
            color, String.format("%.1f", dto.getPercentageUsed()));
    }
}


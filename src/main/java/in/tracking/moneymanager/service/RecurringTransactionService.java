package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.dto.IncomeDTO;
import in.tracking.moneymanager.dto.RecurringTransactionDTO;
import in.tracking.moneymanager.entity.CategoryEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.entity.RecurringTransactionEntity;
import in.tracking.moneymanager.repository.CategoryRepository;
import in.tracking.moneymanager.repository.ProfileRepository;
import in.tracking.moneymanager.repository.RecurringTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing recurring transactions.
 * Includes scheduled jobs to auto-create transactions and send reminders.
 *
 * Optimized for Neon free tier:
 * - Batch processing with limits
 * - Efficient queries with indexes
 * - Jobs run during off-peak hours
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepo;
    private final CategoryRepository categoryRepository;
    private final ProfileRepository profileRepository;
    private final ExpenceService expenceService;
    private final IncomeService incomeService;
    private final ProfileService profileService;
    private final EmailService emailService;

    // Max recurring transactions per user (to prevent abuse)
    private static final int MAX_RECURRING_PER_USER = 50;
    // Max transactions to process per job run (to limit DB load)
    private static final int BATCH_SIZE = 100;

    private static final Set<Integer> VALID_WEEK_DAYS = Set.of(1, 2, 3, 4, 5, 6, 7);
    private static final int MAX_NEXT_EXECUTION_SCAN_DAYS = 400;


    /**
     * Get all recurring transactions for current user.
     */
    public List<RecurringTransactionDTO> getAllRecurring() {
        Long profileId = profileService.getCurrentProfile().getId();
        return recurringRepo.findByProfileIdAndIsActiveTrue(profileId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a new recurring transaction.
     */
    @Transactional
    public RecurringTransactionDTO createRecurring(RecurringTransactionDTO dto) {
        Long profileId = profileService.getCurrentProfile().getId();

        // Check limit
        long count = recurringRepo.countByProfileIdAndIsActiveTrue(profileId);
        if (count >= MAX_RECURRING_PER_USER) {
            throw new RuntimeException("Maximum recurring transactions limit reached (" +
                    MAX_RECURRING_PER_USER + ")");
        }
        validateRecurringInput(dto);
        RecurringTransactionEntity entity = RecurringTransactionEntity.builder()
                .profileId(profileId)
                .name(dto.getName())
                .amount(dto.getAmount())
                .type(dto.getType().toUpperCase())
                .categoryId(dto.getCategoryId())
                .icon(dto.getIcon())
                .frequency(dto.getFrequency().toUpperCase())
                .dayOfPeriod(dto.getDayOfPeriod())
                .weekDaysCsv(toCsv(dto.getWeekDays()))                    // map new field
                .excludedWeekDaysCsv(toCsv(dto.getExcludedWeekDays()))    // map new field
                .startDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now())
                .endDate(dto.getEndDate())
                .isActive(true)
                .sendReminder(dto.getSendReminder() != null ? dto.getSendReminder() : true)
                .reminderDaysBefore(dto.getReminderDaysBefore() != null ? dto.getReminderDaysBefore() : 1)
                .reminderSent(false)
                .build();

        // Calculate next execution date
        entity.setNextExecution(calculateNextExecution(entity, null));

        RecurringTransactionEntity saved = recurringRepo.save(entity);
        log.info("Created recurring transaction: {} for profile {}", dto.getName(), profileId);

        return toDTO(saved);
    }

    /**
     * Update a recurring transaction.
     */
    @Transactional
    public RecurringTransactionDTO updateRecurring(Long id, RecurringTransactionDTO dto) {
        Long profileId = profileService.getCurrentProfile().getId();

        RecurringTransactionEntity entity = recurringRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));

        if (!entity.getProfileId().equals(profileId)) {
            throw new RuntimeException("Access denied");
        }

        validateRecurringInput(dto);

        entity.setName(dto.getName());
        entity.setAmount(dto.getAmount());
        entity.setType(dto.getType().toUpperCase());
        entity.setCategoryId(dto.getCategoryId());
        entity.setIcon(dto.getIcon());
        entity.setFrequency(dto.getFrequency().toUpperCase());
        entity.setDayOfPeriod(dto.getDayOfPeriod());
        entity.setEndDate(dto.getEndDate());
        entity.setSendReminder(dto.getSendReminder() != null ? dto.getSendReminder() : true);
        entity.setReminderDaysBefore(dto.getReminderDaysBefore() != null ? dto.getReminderDaysBefore() : 1);

        // Recalculate next execution if frequency changed
        entity.setNextExecution(calculateNextExecution(entity, entity.getLastExecuted()));

        RecurringTransactionEntity saved = recurringRepo.save(entity);
        log.info("Updated recurring transaction: {} (profile: {})", id, profileId);

        return toDTO(saved);
    }

    /**
     * Delete (deactivate) a recurring transaction.
     */
    @Transactional
    public void deleteRecurring(Long id) {
        Long profileId = profileService.getCurrentProfile().getId();

        RecurringTransactionEntity entity = recurringRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));

        if (!entity.getProfileId().equals(profileId)) {
            throw new RuntimeException("Access denied");
        }

        entity.setIsActive(false);
        recurringRepo.save(entity);
        log.info("Deactivated recurring transaction: {} (profile: {})", id, profileId);
    }

    /**
     * Scheduled job: Execute due recurring transactions.
     * Runs every day at 6 AM IST (off-peak for Indian users).
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void executeRecurringTransactions() {
        log.info("Running recurring transaction executor...");
        LocalDate today = LocalDate.now();

        List<RecurringTransactionEntity> dueTransactions = recurringRepo.findDueTransactions(today);

        // Limit batch size to protect database
        if (dueTransactions.size() > BATCH_SIZE) {
            log.warn("Too many due transactions ({}), processing only {}",
                    dueTransactions.size(), BATCH_SIZE);
            dueTransactions = dueTransactions.subList(0, BATCH_SIZE);
        }

        int processed = 0;
        int failed = 0;
        List<Long> resetReminderIds = new ArrayList<>();

        for (RecurringTransactionEntity recurring : dueTransactions) {
            try {
                // Check if past end date
                if (recurring.getEndDate() != null && today.isAfter(recurring.getEndDate())) {
                    recurring.setIsActive(false);
                    recurringRepo.save(recurring);
                    log.info("Deactivated expired recurring: {}", recurring.getId());
                    continue;
                }

                // Create the actual transaction
                if ("EXPENSE".equals(recurring.getType())) {
                    ExpenceDTO expense = ExpenceDTO.builder()
                            .name(recurring.getName() + " (Auto)")
                            .amount(recurring.getAmount())
                            .date(recurring.getNextExecution())
                            .categoryId(recurring.getCategoryId())
                            .icon(recurring.getIcon())
                            .build();
                    expenceService.addExpenceForProfile(expense, recurring.getProfileId());
                } else if ("INCOME".equals(recurring.getType())) {
                    IncomeDTO income = IncomeDTO.builder()
                            .name(recurring.getName() + " (Auto)")
                            .amount(recurring.getAmount())
                            .date(recurring.getNextExecution())
                            .categoryId(recurring.getCategoryId())
                            .icon(recurring.getIcon())
                            .build();
                    incomeService.addIncomeForProfile(income, recurring.getProfileId());
                }

                // Update last executed and calculate next execution
                recurring.setLastExecuted(recurring.getNextExecution());
                recurring.setNextExecution(calculateNextExecution(recurring, recurring.getLastExecuted()));
                recurring.setReminderSent(false);  // Reset reminder for next period
                recurringRepo.save(recurring);

                resetReminderIds.add(recurring.getId());
                processed++;

                log.debug("Executed recurring: {} -> {} for profile {}",
                        recurring.getName(), recurring.getType(), recurring.getProfileId());

            } catch (Exception e) {
                log.error("Failed to execute recurring {}: {}", recurring.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Recurring executor complete. Processed: {}, Failed: {}", processed, failed);
    }

    /**
     * Scheduled job: Send reminders for upcoming transactions.
     * Runs every day at 9 AM IST.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendReminders() {
        log.info("Running bill reminder job...");
        LocalDate today = LocalDate.now();
        LocalDate reminderDate = today.plusDays(3);  // Look ahead 3 days

        List<RecurringTransactionEntity> upcoming = recurringRepo
                .findTransactionsNeedingReminder(today, reminderDate);

        int sent = 0;
        for (RecurringTransactionEntity recurring : upcoming) {
            try {
                // Get profile for email
                ProfileEntity profile = profileRepository.findById(recurring.getProfileId())
                        .orElse(null);

                if (profile != null && profile.getEmail() != null) {
                    // Check if within reminder window
                    long daysUntil = java.time.temporal.ChronoUnit.DAYS
                            .between(today, recurring.getNextExecution());

                    if (daysUntil <= recurring.getReminderDaysBefore()) {
                        String subject = "🔔 Upcoming: " + recurring.getName() +
                                " (₹" + recurring.getAmount() + ")";
                        String body = buildReminderEmail(recurring, profile.getFullname(), daysUntil);

                        emailService.sendEmail(profile.getEmail(), subject, body);

                        recurring.setReminderSent(true);
                        recurringRepo.save(recurring);
                        sent++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send reminder for {}: {}", recurring.getId(), e.getMessage());
            }
        }

        log.info("Reminder job complete. Sent: {} reminders", sent);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate next execution date based on full scheduling rules.
     * Supports:
     * - WEEKLY on specific weekdays
     * - DAILY excluding specific weekdays
     * - Existing dayOfPeriod behavior for backward compatibility
     */
    private LocalDate calculateNextExecution(RecurringTransactionEntity entity, LocalDate lastExecuted) {
        LocalDate today = LocalDate.now();

        // Start scanning from next day after last execution, or from start date for new records.
        LocalDate scanDate = (lastExecuted != null ? lastExecuted.plusDays(1) : entity.getStartDate());
        if (scanDate.isBefore(today)) {
            scanDate = today;
        }

        // Bounded scan prevents accidental infinite loop on bad config.
        for (int i = 0; i < MAX_NEXT_EXECUTION_SCAN_DAYS; i++) {
            if (matchesSchedule(entity, scanDate)) {
                return scanDate;
            }
            scanDate = scanDate.plusDays(1);
        }

        throw new IllegalStateException("Unable to compute next execution within safe scan window");
    }

    private boolean matchesSchedule(RecurringTransactionEntity entity, LocalDate date) {
        if (date.isBefore(entity.getStartDate())) return false;
        if (entity.getEndDate() != null && date.isAfter(entity.getEndDate())) return false;

        String frequency = entity.getFrequency();

        return switch (frequency) {
            case "DAILY" -> {
                Set<Integer> excluded = toSet(entity.getExcludedWeekDaysCsv());
                int dow = date.getDayOfWeek().getValue();
                yield !excluded.contains(dow);
            }

            case "WEEKLY" -> {
                Set<Integer> allowed = toSet(entity.getWeekDaysCsv());

                // Backward compatibility: old data may only have dayOfPeriod
                if (allowed.isEmpty() && entity.getDayOfPeriod() != null) {
                    allowed = Set.of(entity.getDayOfPeriod());
                }

                int dow = date.getDayOfWeek().getValue();
                yield allowed.contains(dow);
            }

            case "MONTHLY" -> {
                if (entity.getDayOfPeriod() == null) yield false;
                int target = Math.min(entity.getDayOfPeriod(), date.lengthOfMonth());
                yield date.getDayOfMonth() == target;
            }

            case "YEARLY" -> {
                LocalDate start = entity.getStartDate();
                int targetDay = Math.min(start.getDayOfMonth(), date.lengthOfMonth());
                yield date.getMonth() == start.getMonth() && date.getDayOfMonth() == targetDay;
            }

            default -> false;
        };
    }

    private void validateRecurringInput(RecurringTransactionDTO dto) {
        String frequency = dto.getFrequency() == null ? "" : dto.getFrequency().toUpperCase();

        validateWeekDays(dto.getWeekDays(), "weekDays");
        validateWeekDays(dto.getExcludedWeekDays(), "excludedWeekDays");

        if ("WEEKLY".equals(frequency)) {
            boolean hasWeekDays = dto.getWeekDays() != null && !dto.getWeekDays().isEmpty();
            boolean hasLegacyDay = dto.getDayOfPeriod() != null;

            if (!hasWeekDays && !hasLegacyDay) {
                throw new RuntimeException("For WEEKLY frequency, provide weekDays or dayOfPeriod");
            }
            if (hasWeekDays && hasLegacyDay) {
                throw new RuntimeException("For WEEKLY frequency, use either weekDays or dayOfPeriod, not both");
            }
        }

        if ("DAILY".equals(frequency) && dto.getDayOfPeriod() != null) {
            throw new RuntimeException("dayOfPeriod is not applicable for DAILY frequency");
        }

        if ("MONTHLY".equals(frequency)) {
            if (dto.getDayOfPeriod() == null || dto.getDayOfPeriod() < 1 || dto.getDayOfPeriod() > 31) {
                throw new RuntimeException("For MONTHLY frequency, dayOfPeriod must be between 1 and 31");
            }
        }
    }

    private void validateWeekDays(List<Integer> days, String fieldName) {
        if (days == null || days.isEmpty()) return;
        for (Integer d : days) {
            if (d == null || !VALID_WEEK_DAYS.contains(d)) {
                throw new RuntimeException(fieldName + " must contain values between 1 and 7");
            }
        }
    }

    private String toCsv(List<Integer> days) {
        if (days == null || days.isEmpty()) return null;
        return days.stream()
                .filter(VALID_WEEK_DAYS::contains)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Set<Integer> toSet(String csv) {
        if (csv == null || csv.isBlank()) return new HashSet<>();
        Set<Integer> result = new HashSet<>();
        for (String s : csv.split(",")) {
            try {
                int value = Integer.parseInt(s.trim());
                if (VALID_WEEK_DAYS.contains(value)) result.add(value);
            } catch (NumberFormatException ignored) {
                // Ignore malformed values to keep backward compatibility robust.
            }
        }
        return result;
    }

    /**
     * Convert entity to DTO.
     */
    private RecurringTransactionDTO toDTO(RecurringTransactionEntity entity) {
        String categoryName = null;
        if (entity.getCategoryId() != null) {
            categoryName = categoryRepository.findById(entity.getCategoryId())
                    .map(CategoryEntity::getName)
                    .orElse(null);
        }

        return RecurringTransactionDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .amount(entity.getAmount())
                .type(entity.getType())
                .categoryId(entity.getCategoryId())
                .categoryName(categoryName)
                .icon(entity.getIcon())
                .frequency(entity.getFrequency())
                .dayOfPeriod(entity.getDayOfPeriod())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .nextExecution(entity.getNextExecution())
                .lastExecuted(entity.getLastExecuted())
                .isActive(entity.getIsActive())
                .sendReminder(entity.getSendReminder())
                .reminderDaysBefore(entity.getReminderDaysBefore())
                .build();
    }

    /**
     * Build HTML email body for bill reminder.
     */
    private String buildReminderEmail(RecurringTransactionEntity recurring,
                                       String userName, long daysUntil) {
        String timeText = daysUntil == 0 ? "today" :
                         daysUntil == 1 ? "tomorrow" : "in " + daysUntil + " days";

        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2 style="color: #007bff;">📅 Bill Reminder</h2>
                <p>Hi %s,</p>
                <p>This is a reminder that <strong>%s</strong> is due %s.</p>
                <table style="border-collapse: collapse; margin: 20px 0;">
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Amount:</td>
                        <td style="padding: 8px; border: 1px solid #ddd;"><strong>₹%s</strong></td></tr>
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Due Date:</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">%s</td></tr>
                    <tr><td style="padding: 8px; border: 1px solid #ddd;">Type:</td>
                        <td style="padding: 8px; border: 1px solid #ddd;">%s</td></tr>
                </table>
                <p>This transaction will be automatically recorded on the due date.</p>
                <p>Best regards,<br>Money Manager Team</p>
            </body>
            </html>
            """,
            userName, recurring.getName(), timeText,
            recurring.getAmount(), recurring.getNextExecution(), recurring.getType());
    }

}


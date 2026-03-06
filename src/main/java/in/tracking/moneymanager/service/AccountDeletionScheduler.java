package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled service for permanently deleting accounts after grace period.
 * Runs daily at 2 AM to clean up accounts scheduled for deletion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionScheduler {

    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final ExpenceRepository expenceRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetGoalRepository budgetGoalRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final EmailService emailService;

    /**
     * Run daily at 2 AM to delete accounts that have passed the 3-day grace period.
     */
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    @Transactional
    public void processScheduledDeletions() {
        log.info("Starting scheduled account deletion process...");

        List<ProfileEntity> profilesToDelete = profileService.getProfilesScheduledForDeletion();

        if (profilesToDelete.isEmpty()) {
            log.info("No accounts scheduled for deletion.");
            return;
        }

        log.info("Found {} accounts to permanently delete", profilesToDelete.size());

        for (ProfileEntity profile : profilesToDelete) {
            try {
                permanentlyDeleteAccount(profile);
                log.info("Successfully deleted account: {} ({})", profile.getId(), profile.getEmail());
            } catch (Exception e) {
                log.error("Failed to delete account {}: {}", profile.getId(), e.getMessage());
            }
        }

        log.info("Completed scheduled account deletion process.");
    }

    /**
     * Permanently delete an account and all associated data.
     * Deletes data in correct order to respect foreign key constraints.
     */
    @Transactional
    public void permanentlyDeleteAccount(ProfileEntity profile) {
        Long profileId = profile.getId();
        String email = profile.getEmail();
        String fullname = profile.getFullname();

        log.info("Permanently deleting account {} ({})", profileId, email);

        // Delete all associated data in correct order (child tables first)

        // 1. Delete bank transactions
        bankTransactionRepository.deleteByProfileId(profileId);

        // 2. Delete budget goals
        budgetGoalRepository.deleteByProfileId(profileId);

        // 3. Delete recurring transactions
        recurringTransactionRepository.deleteByProfileId(profileId);

        // 4. Delete expenses
        expenceRepository.deleteByProfileId(profileId);

        // 5. Delete incomes
        incomeRepository.deleteByProfileId(profileId);

        // 6. Delete categories (user's custom categories)
        categoryRepository.deleteByProfileId(profileId);

        // 7. Delete payment history
        paymentHistoryRepository.deleteByProfileId(profileId);

        // 8. Delete subscriptions
        subscriptionRepository.deleteByProfileId(profileId);

        // 9. Finally, delete the profile
        profileRepository.delete(profile);

        // Send confirmation email (to the deleted email address)
        try {
            String subject = "Account Deleted - Money Manager";
            String body = String.format(
                "Hello %s,\n\n" +
                "Your Money Manager account has been permanently deleted as requested.\n\n" +
                "All your data including expenses, incomes, budgets, and AI insights have been removed.\n\n" +
                "If you didn't request this deletion, please contact our support immediately.\n\n" +
                "Thank you for using Money Manager.\n\n" +
                "Best regards,\nMoney Manager Team",
                fullname
            );
            emailService.sendEmail(email, subject, body);
        } catch (Exception e) {
            log.warn("Failed to send deletion confirmation email to {}: {}", email, e.getMessage());
        }

        log.info("Account {} permanently deleted", profileId);
    }
}


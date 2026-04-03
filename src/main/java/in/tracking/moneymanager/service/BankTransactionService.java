package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.BankTransactionDTO;
import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.dto.IncomeDTO;
import in.tracking.moneymanager.dto.SubscriptionDTO;
import in.tracking.moneymanager.entity.BankTransactionEntity;
import in.tracking.moneymanager.repository.BankTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Service for bank transaction operations.
 * Handles CSV import, categorization, and conversion to expenses/incomes.
 *
 * Import limits by subscription:
 * - FREE: 0 imports
 * - BASIC: 3 imports/month
 * - PREMIUM: Unlimited
 */
@Service
@Slf4j
public class BankTransactionService {

    private final BankTransactionRepository bankTransactionRepository;
    private final CsvParserService csvParserService;
    private final ProfileService profileService;
    private final SubscriptionService subscriptionService;
    private final ExpenceService expenceService;
    private final IncomeService incomeService;

    private static final long MAX_RANGE_DAYS = 366;

    @Autowired
    public BankTransactionService(
            BankTransactionRepository bankTransactionRepository,
            CsvParserService csvParserService,
            ProfileService profileService,
            SubscriptionService subscriptionService,
            @Lazy ExpenceService expenceService,
            @Lazy IncomeService incomeService) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.csvParserService = csvParserService;
        this.profileService = profileService;
        this.subscriptionService = subscriptionService;
        this.expenceService = expenceService;
        this.incomeService = incomeService;
    }

    /**
     * Import transactions from CSV file.
     * Checks subscription limits before processing.
     *
     * @param file Uploaded CSV file
     * @param bankName Bank name: SBI, HDFC, ICICI, AXIS, KOTAK, or GENERIC
     * @return Import result with count and status
     */
    @Transactional
    public Map<String, Object> importCsv(MultipartFile file, String bankName) {
        Long profileId = profileService.getCurrentProfile().getId();

        // Check if user has remaining imports
        checkImportLimit(profileId);

        // Parse CSV file
        List<BankTransactionEntity> transactions = csvParserService.parseCSV(file, profileId, bankName);

        if (transactions.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "No valid transactions found in the CSV file"
            );
        }

        // Save all transactions
        List<BankTransactionEntity> saved = bankTransactionRepository.saveAll(transactions);

        log.info("Imported {} transactions for profile {} from {} bank",
                saved.size(), profileId, bankName);

        // Count by type
        long credits = saved.stream().filter(t -> "CREDIT".equals(t.getType())).count();
        long debits = saved.stream().filter(t -> "DEBIT".equals(t.getType())).count();

        return Map.of(
                "success", true,
                "totalImported", saved.size(),
                "credits", credits,
                "debits", debits,
                "message", String.format("Successfully imported %d transactions (%d credits, %d debits)",
                        saved.size(), credits, debits)
        );
    }

    /**
     * Get bank transactions with pagination.
     *
     * @param pageable Pagination parameters
     * @return Page of bank transactions
     */
    public Page<BankTransactionDTO> getTransactions(Pageable pageable) {
        Long profileId = profileService.getCurrentProfile().getId();
        return bankTransactionRepository
                .findByProfileIdOrderByTransactionDateDesc(profileId, pageable)
                .map(this::toDTO);
    }

    /**
     * Get unconverted transactions (not yet converted to expense/income).
     *
     * @return List of unconverted transactions
     */
    public List<BankTransactionDTO> getUnconvertedTransactions() {
        Long profileId = profileService.getCurrentProfile().getId();
        return bankTransactionRepository
                .findByProfileIdAndIsConvertedFalse(profileId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Categorize and convert a bank transaction to expense/income.
     * This creates the actual expense or income record.
     *
     * @param id Transaction ID
     * @param categoryId Category ID to assign
     * @return Updated transaction
     */
    @Transactional
    public BankTransactionDTO categorizeTransaction(Long id, Long categoryId) {
        BankTransactionEntity txn = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        Long profileId = profileService.getCurrentProfile().getId();

        // Verify ownership
        if (!txn.getProfileId().equals(profileId)) {
            throw new RuntimeException("Access denied - not your transaction");
        }

        // Check if already converted
        if (txn.getIsConverted()) {
            throw new RuntimeException("Transaction already converted");
        }

        txn.setCategoryId(categoryId);

        // Create a descriptive name
        String name = txn.getMerchantName() != null && !txn.getMerchantName().isEmpty()
                ? txn.getMerchantName()
                : txn.getDescription();

        // Truncate if too long
        if (name.length() > 100) {
            name = name.substring(0, 97) + "...";
        }

        // Create actual expense or income based on transaction type
        Long linkedId;
        if ("DEBIT".equals(txn.getType())) {
            // Create expense
            ExpenceDTO expenseDTO = ExpenceDTO.builder()
                    .name(name)
                    .amount(txn.getAmount())
                    .date(txn.getTransactionDate())
                    .categoryId(categoryId)
                    .build();
            ExpenceDTO savedExpense = expenceService.addExpenceForProfile(expenseDTO, profileId);
            linkedId = savedExpense.getId();
            log.info("Created expense {} from bank transaction {}", linkedId, id);
        } else {
            // Create income
            IncomeDTO incomeDTO = IncomeDTO.builder()
                    .name(name)
                    .amount(txn.getAmount())
                    .date(txn.getTransactionDate())
                    .categoryId(categoryId)
                    .build();
            IncomeDTO savedIncome = incomeService.addIncomeForProfile(incomeDTO, profileId);
            linkedId = savedIncome.getId();
            log.info("Created income {} from bank transaction {}", linkedId, id);
        }

        // Mark as converted
        txn.setIsConverted(true);
        txn.setLinkedTransactionId(linkedId);
        BankTransactionEntity saved = bankTransactionRepository.save(txn);

        log.info("Categorized and converted transaction {} to category {}, linked to {}", id, categoryId, linkedId);
        return toDTO(saved);
    }

    /**
     * Delete a single bank transaction.
     *
     * @param id Transaction ID to delete
     * @return true if deleted
     */
    @Transactional
    public boolean deleteTransaction(Long id) {
        Long profileId = profileService.getCurrentProfile().getId();

        BankTransactionEntity txn = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Verify ownership
        if (!txn.getProfileId().equals(profileId)) {
            throw new RuntimeException("Access denied - not your transaction");
        }

        bankTransactionRepository.delete(txn);
        log.info("Deleted bank transaction {} for profile {}", id, profileId);
        return true;
    }

    /**
     * Delete multiple bank transactions.
     *
     * @param ids List of transaction IDs to delete
     * @return Number of deleted transactions
     */
    @Transactional
    public int deleteTransactions(List<Long> ids) {
        Long profileId = profileService.getCurrentProfile().getId();
        int deleted = 0;

        for (Long id : ids) {
            try {
                BankTransactionEntity txn = bankTransactionRepository.findById(id).orElse(null);
                if (txn != null && txn.getProfileId().equals(profileId)) {
                    bankTransactionRepository.delete(txn);
                    deleted++;
                }
            } catch (Exception e) {
                log.warn("Failed to delete transaction {}: {}", id, e.getMessage());
            }
        }

        log.info("Deleted {} bank transactions for profile {}", deleted, profileId);
        return deleted;
    }

    @Transactional(readOnly = true)
    public Page<BankTransactionDTO> getTransactionsByDateRange(
            LocalDate startDate, LocalDate endDate, Pageable pageable) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        Long profileId = profileService.getCurrentProfile().getId();

        return bankTransactionRepository
                .findByProfileIdAndTransactionDateBetweenOrderByTransactionDateDesc(
                        profileId, startDate, endDate, pageable)
                .map(this::toDTO);
    }


    /**
     * Delete all unconverted bank transactions.
     *
     * @return Number of deleted transactions
     */
    @Transactional
    public int deleteAllUnconverted() {
        Long profileId = profileService.getCurrentProfile().getId();
        List<BankTransactionEntity> unconverted = bankTransactionRepository
                .findByProfileIdAndIsConvertedFalse(profileId);

        int count = unconverted.size();
        bankTransactionRepository.deleteAll(unconverted);
        log.info("Deleted all {} unconverted bank transactions for profile {}", count, profileId);
        return count;
    }

    /**
     * Mark a transaction as converted (linked to expense/income).
     *
     * @param id Transaction ID
     * @param linkedTransactionId ID of the created expense/income
     */
    @Transactional
    public void markAsConverted(Long id, Long linkedTransactionId) {
        BankTransactionEntity txn = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        txn.setIsConverted(true);
        txn.setLinkedTransactionId(linkedTransactionId);
        bankTransactionRepository.save(txn);

        log.info("Marked transaction {} as converted, linked to {}", id, linkedTransactionId);
    }

    /**
     * Get import usage statistics for current month.
     *
     * @return Map with used and limit counts
     */
    public Map<String, Object> getImportUsage() {
        Long profileId = profileService.getCurrentProfile().getId();
        SubscriptionDTO subscription = subscriptionService.getCurrentSubscription();

        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        Long used = bankTransactionRepository.countByProfileIdAndCreatedAtAfter(profileId, monthStart);

        int limit = subscription.getCsvImportsLimit();

        return Map.of(
                "used", used,
                "limit", limit,
                "remaining", Math.max(0, limit - used.intValue()),
                "unlimited", limit >= 999
        );
    }

    // ==================== Helper Methods ====================

    /**
     * Check if user has remaining imports for this month.
     */
    private void checkImportLimit(Long profileId) {
        SubscriptionDTO subscription = subscriptionService.getCurrentSubscription();
        int limit = subscription.getCsvImportsLimit();

        if (limit == 0) {
            throw new RuntimeException(
                    "CSV import requires BASIC or PREMIUM subscription. " +
                    "Please upgrade to import bank statements.");
        }

        // Skip check for unlimited plans (999)
        if (limit >= 999) {
            return;
        }

        // Count imports this month
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        Long importsThisMonth = bankTransactionRepository
                .countByProfileIdAndCreatedAtAfter(profileId, monthStart);

        if (importsThisMonth >= limit) {
            throw new RuntimeException(String.format(
                    "Monthly CSV import limit reached (%d/%d). " +
                    "Please upgrade to PREMIUM for unlimited imports or wait until next month.",
                    importsThisMonth, limit));
        }

        log.info("User {} importing CSV. Used: {}/{}", profileId, importsThisMonth + 1, limit);
    }

    /**
     * Convert entity to DTO.
     */
    private BankTransactionDTO toDTO(BankTransactionEntity entity) {
        return BankTransactionDTO.builder()
                .id(entity.getId())
                .bankName(entity.getBankName())
                .transactionDate(entity.getTransactionDate())
                .description(entity.getDescription())
                .referenceNumber(entity.getReferenceNumber())
                .amount(entity.getAmount())
                .type(entity.getType())
                .balance(entity.getBalance())
                .categoryId(entity.getCategoryId())
                .suggestedCategory(entity.getSuggestedCategory())
                .merchantName(entity.getMerchantName())
                .isConverted(entity.getIsConverted())
                .build();
    }
}


package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.dto.PagedResponse;
import in.tracking.moneymanager.dto.SplitDTO;
import in.tracking.moneymanager.entity.AccountEntity;
import in.tracking.moneymanager.entity.CategoryEntity;
import in.tracking.moneymanager.entity.ExpenceEntity;
import in.tracking.moneymanager.entity.ExpenseSplitEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.dto.event.TransactionEvent;
import in.tracking.moneymanager.event.ExpenseCreatedEvent;
import in.tracking.moneymanager.repository.CategoryRepository;
import in.tracking.moneymanager.repository.ExpenceRepository;
import in.tracking.moneymanager.repository.ExpenseSplitRepository;
import in.tracking.moneymanager.service.event.TransactionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenceService {

    private final CategoryRepository categoryRepository;
    private final ExpenceRepository expenceRepository;
    private final ProfileService profileService;
    private final AccountService accountService;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final TransactionEventPublisher transactionEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public ExpenceDTO addExpence(ExpenceDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getCategoryId()));
        AccountEntity account = dto.getAccountId() != null
                ? accountService.getAccountForProfile(dto.getAccountId(), profile.getId())
                : null;
        ExpenceEntity newExpence = toEntity(dto, profile, category, account);
        ExpenceEntity savedExpence = expenceRepository.save(newExpence);
        if (account != null) {
            accountService.adjustBalance(account, savedExpence.getAmount().negate());
        }
        if (dto.getSplits() != null && !dto.getSplits().isEmpty()) {
            List<ExpenseSplitEntity> splits = dto.getSplits().stream()
                    .map(s -> ExpenseSplitEntity.builder()
                            .expense(savedExpence)
                            .participantName(s.getParticipantName())
                            .shareAmount(s.getShareAmount())
                            .build())
                    .toList();
            expenseSplitRepository.saveAll(splits);
        }
        // Decoupled from BudgetGoalService: it listens for this event to check budget thresholds
        applicationEventPublisher.publishEvent(
                new ExpenseCreatedEvent(this, profile.getId(), category.getId(), savedExpence.getAmount()));
        transactionEventPublisher.publish(TransactionEvent.builder()
                .profileId(profile.getId())
                .transactionId(savedExpence.getId())
                .type("EXPENSE")
                .action("CREATED")
                .amount(savedExpence.getAmount())
                .categoryName(category.getName())
                .occurredAt(LocalDateTime.now())
                .build());
        return toDTO(savedExpence);
    }

    //Retrieves all expences for current month/based on the start date or end date
    public List<ExpenceDTO> getCurrentMonthExpenceForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        LocalDate now = LocalDate.now();
        return toDTOList(expenceRepository.findByProfileIdAndDateBetween(profile.getId(), now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth())));
    }

    //delete expence by id for current user
    @Transactional
    public void deleteExpence(long expenceId){
        ProfileEntity profile = profileService.getCurrentProfile();
        ExpenceEntity entity = expenceRepository.findById(expenceId)
                .orElseThrow(() -> new RuntimeException("Expence not found with id: " + expenceId));
        if(!entity.getProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("You don't have permission to delete this expence");
        }
        if (entity.getAccount() != null) {
            accountService.adjustBalance(entity.getAccount(), entity.getAmount());
        }
        expenseSplitRepository.deleteByExpenseId(expenceId);
        expenceRepository.deleteById(expenceId);
        transactionEventPublisher.publish(TransactionEvent.builder()
                .profileId(profile.getId())
                .transactionId(expenceId)
                .type("EXPENSE")
                .action("DELETED")
                .amount(entity.getAmount())
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    //get latest 5 expence for current user
    public List<ExpenceDTO> getLatest5ExpenceForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        return toDTOList(expenceRepository.findTop5ByProfileIdOrderByDateDesc(profile.getId()));
    }

    //get total expances for current user
    public BigDecimal getTotalExpenceForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = expenceRepository.findTotalExpenceByProfileId(profile.getId());
        return total != null ? total : BigDecimal.ZERO;
    }

    //filter expences, optionally narrowed to one tag
    public List<ExpenceDTO> filterExpences(LocalDate startDate, LocalDate endDate, String keyword, String tag, Sort sort){
        List<ExpenceEntity> list = expenceRepository.searchByProfileAndFilters(
                profileService.getCurrentProfile().getId(), startDate, endDate, keyword, tag, sort);
        return toDTOList(list);
    }

    //Notificationa
    public List<ExpenceDTO> getExpenceForUserOnDate(Long profileId, LocalDate date){
        return toDTOList(expenceRepository.findByProfileIdAndDate(profileId, date));
    }

    public BigDecimal getTotalExpenceForDateRangeForCurrentUser(LocalDate startDate, LocalDate endDate) {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = expenceRepository.findTotalExpenceByProfileIdAndDateBetween(profile.getId(), startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<ExpenceDTO> getAllExpenceForCurrentUserOrderByDateDesc() {
        ProfileEntity profile = profileService.getCurrentProfile();
        return toDTOList(expenceRepository.findByProfileIdOrderByDateDesc(profile.getId()));
    }

    //paginated expense history for current user
    public PagedResponse<ExpenceDTO> getExpencesPaginated(Pageable pageable) {
        ProfileEntity profile = profileService.getCurrentProfile();
        Page<ExpenceEntity> page = expenceRepository.findByProfileId(profile.getId(), pageable);
        Map<Long, List<SplitDTO>> splitsByExpenseId = loadSplitsByExpenseIds(page.getContent());
        return PagedResponse.of(page, e -> toDTO(e, splitsByExpenseId.get(e.getId())));
    }

    //paginated + filtered expense history, optionally narrowed to one tag
    public PagedResponse<ExpenceDTO> filterExpencesPaginated(LocalDate startDate, LocalDate endDate, String keyword, String tag, Pageable pageable) {
        ProfileEntity profile = profileService.getCurrentProfile();
        Page<ExpenceEntity> page = expenceRepository.searchByProfileAndFilters(
                profile.getId(), startDate, endDate, keyword, tag, pageable);
        Map<Long, List<SplitDTO>> splitsByExpenseId = loadSplitsByExpenseIds(page.getContent());
        return PagedResponse.of(page, e -> toDTO(e, splitsByExpenseId.get(e.getId())));
    }

    //mark a split as settled ("they paid you back")
    @Transactional
    public SplitDTO settleSplit(Long expenseId, Long splitId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        ExpenceEntity expense = expenceRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expence not found with id: " + expenseId));
        if (!expense.getProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("You don't have permission to modify this expence");
        }
        ExpenseSplitEntity split = expenseSplitRepository.findById(splitId)
                .orElseThrow(() -> new RuntimeException("Split not found with id: " + splitId));
        if (!split.getExpense().getId().equals(expenseId)) {
            throw new RuntimeException("Split does not belong to this expence");
        }
        split.setIsSettled(true);
        split.setSettledAt(LocalDateTime.now());
        return toSplitDTO(expenseSplitRepository.save(split));
    }

    //"who owes you" - unsettled split totals per participant, across all of the current user's expenses
    public Map<String, BigDecimal> getSplitSummaryForCurrentUser() {
        Long profileId = profileService.getCurrentProfile().getId();
        Map<String, BigDecimal> summary = new LinkedHashMap<>();
        for (Object[] row : expenseSplitRepository.sumUnsettledByParticipant(profileId)) {
            summary.put((String) row[0], (BigDecimal) row[1]);
        }
        return summary;
    }

    /**
     * Add expense for a specific profile (used by recurring transactions).
     * Does not require authentication context.
     */
    @Transactional
    public ExpenceDTO addExpenceForProfile(ExpenceDTO dto, Long profileId) {
        ProfileEntity profile = profileService.getProfileById(profileId);
        CategoryEntity category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
        }
        ExpenceEntity newExpence = ExpenceEntity.builder()
                .name(dto.getName())
                .icon(dto.getIcon())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .profile(profile)
                .category(category)
                .build();
        ExpenceEntity savedExpence = expenceRepository.save(newExpence);
        return toDTO(savedExpence);
    }


    //helper method to calculate total expence for a given category
    private ExpenceEntity toEntity(ExpenceDTO expenceDTO, ProfileEntity profile, CategoryEntity category, AccountEntity account) {
        return ExpenceEntity.builder()
                .name(expenceDTO.getName())
                .icon(expenceDTO.getIcon())
                .amount(expenceDTO.getAmount())
                .date(expenceDTO.getDate())
                .attachmentUrl(expenceDTO.getAttachmentUrl())
                .profile(profile)
                .category(category)
                .account(account)
                .tags(expenceDTO.getTags() != null ? new java.util.HashSet<>(expenceDTO.getTags()) : new java.util.HashSet<>())
                .build();
    }

    // Single-entity convenience (addExpence/addExpenceForProfile/settleSplit's caller) - one query is fine
    // for a single item. List-returning methods must go through toDTOList instead to avoid N+1.
    private ExpenceDTO toDTO(ExpenceEntity entity) {
        List<SplitDTO> splits = expenseSplitRepository.findByExpenseId(entity.getId())
                .stream().map(this::toSplitDTO).toList();
        return toDTO(entity, splits);
    }

    // Batch-loads splits for a whole list in one query instead of N+1 (was previously one
    // findByExpenseId call per entity inside toDTO - that turned every expense list endpoint,
    // even for users with zero splits, into 1+N DB round-trips).
    private List<ExpenceDTO> toDTOList(List<ExpenceEntity> entities) {
        Map<Long, List<SplitDTO>> splitsByExpenseId = loadSplitsByExpenseIds(entities);
        return entities.stream().map(e -> toDTO(e, splitsByExpenseId.get(e.getId()))).toList();
    }

    private Map<Long, List<SplitDTO>> loadSplitsByExpenseIds(List<ExpenceEntity> entities) {
        if (entities.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = entities.stream().map(ExpenceEntity::getId).toList();
        return expenseSplitRepository.findByExpenseIdIn(ids).stream()
                .collect(Collectors.groupingBy(s -> s.getExpense().getId(),
                        Collectors.mapping(this::toSplitDTO, Collectors.toList())));
    }

    private ExpenceDTO toDTO(ExpenceEntity entity, List<SplitDTO> splits) {
        return ExpenceDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : "N/A")
                .amount(entity.getAmount())
                .date(entity.getDate())
                .attachmentUrl(entity.getAttachmentUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                // Copy out of the lazy-loaded proxy while the session is still open (toDTO always
                // runs inside this class's @Transactional boundary) - passing the raw Hibernate
                // collection through would throw LazyInitializationException when Jackson
                // serializes the response after the transaction/session has already closed.
                .tags(entity.getTags() != null ? new java.util.HashSet<>(entity.getTags()) : null)
                .accountId(entity.getAccount() != null ? entity.getAccount().getId() : null)
                .accountName(entity.getAccount() != null ? entity.getAccount().getName() : null)
                .splits(splits == null || splits.isEmpty() ? null : splits)
                .build();
    }

    private SplitDTO toSplitDTO(ExpenseSplitEntity entity) {
        return SplitDTO.builder()
                .id(entity.getId())
                .participantName(entity.getParticipantName())
                .shareAmount(entity.getShareAmount())
                .isSettled(entity.getIsSettled())
                .settledAt(entity.getSettledAt())
                .build();
    }
}

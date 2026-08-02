package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.AccountDTO;
import in.tracking.moneymanager.entity.AccountEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ProfileService profileService;

    @CacheEvict(value = "accounts", allEntries = true)
    public AccountDTO saveAccount(AccountDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        if (accountRepository.existsByNameAndProfileId(dto.getName(), profile.getId())) {
            throw new RuntimeException("Account with this name already exists");
        }
        AccountEntity entity = AccountEntity.builder()
                .name(dto.getName())
                .type(dto.getType())
                .icon(dto.getIcon())
                .balance(dto.getBalance() != null ? dto.getBalance() : BigDecimal.ZERO)
                .profile(profile)
                .build();
        return toDTO(accountRepository.save(entity));
    }

    @Cacheable(value = "accounts", key = "#root.target.currentProfileId()")
    public List<AccountDTO> getAccountsForCurrentUser() {
        return accountRepository.findByProfileId(currentProfileId())
                .stream().map(this::toDTO).toList();
    }

    @CacheEvict(value = "accounts", allEntries = true)
    public AccountDTO updateAccount(Long accountId, AccountDTO dto) {
        AccountEntity existing = accountRepository.findByIdAndProfileId(accountId, currentProfileId())
                .orElseThrow(() -> new RuntimeException("Account not found or you don't have permission to update it"));
        existing.setName(dto.getName());
        existing.setType(dto.getType());
        existing.setIcon(dto.getIcon());
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }
        return toDTO(accountRepository.save(existing));
    }

    @CacheEvict(value = "accounts", allEntries = true)
    public void deleteAccount(Long accountId) {
        AccountEntity existing = accountRepository.findByIdAndProfileId(accountId, currentProfileId())
                .orElseThrow(() -> new RuntimeException("Account not found or you don't have permission to delete it"));
        accountRepository.delete(existing);
    }

    public BigDecimal getNetWorthForCurrentUser() {
        return accountRepository.sumBalanceByProfileId(currentProfileId());
    }

    /**
     * Loads an account owned by the given profile, or throws - used by
     * ExpenceService/IncomeService to validate an accountId supplied on create,
     * the same way they already validate categoryId.
     */
    public AccountEntity getAccountForProfile(Long accountId, Long profileId) {
        return accountRepository.findByIdAndProfileId(accountId, profileId)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + accountId));
    }

    /**
     * Adjusts an account's running balance by delta (positive for income/refund,
     * negative for expense) and persists it. Informal running balance, not
     * double-entry - same convention as BankTransactionEntity's balance snapshot.
     */
    @Transactional
    @CacheEvict(value = "accounts", allEntries = true)
    public void adjustBalance(AccountEntity account, BigDecimal delta) {
        account.setBalance(account.getBalance().add(delta));
        accountRepository.save(account);
    }

    // Cache key helper - exposed for SpEL in cache annotations above
    public Long currentProfileId() {
        return profileService.getCurrentProfile().getId();
    }

    private AccountDTO toDTO(AccountEntity entity) {
        return AccountDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .icon(entity.getIcon())
                .balance(entity.getBalance())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.AccountDTO;
import in.tracking.moneymanager.entity.NetWorthSnapshotEntity;
import in.tracking.moneymanager.repository.NetWorthSnapshotRepository;
import in.tracking.moneymanager.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final NetWorthSnapshotRepository netWorthSnapshotRepository;

    @PostMapping
    public ResponseEntity<AccountDTO> saveAccount(@RequestBody AccountDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.saveAccount(dto));
    }

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAccounts() {
        return ResponseEntity.ok(accountService.getAccountsForCurrentUser());
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<AccountDTO> updateAccount(@PathVariable Long accountId, @RequestBody AccountDTO dto) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, dto));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
        accountService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/net-worth")
    public ResponseEntity<Map<String, BigDecimal>> getNetWorth() {
        BigDecimal netWorth = accountService.getNetWorthForCurrentUser();
        return ResponseEntity.ok(Map.of("netWorth", netWorth != null ? netWorth : BigDecimal.ZERO));
    }

    @GetMapping("/net-worth-trend")
    public ResponseEntity<List<NetWorthSnapshotEntity>> getNetWorthTrend(
            @RequestParam(defaultValue = "6") int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        return ResponseEntity.ok(netWorthSnapshotRepository
                .findByProfileIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        accountService.currentProfileId(), startDate, endDate));
    }
}

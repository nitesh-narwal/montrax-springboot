package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.dto.IncomeDTO;
import in.tracking.moneymanager.dto.RecentTransactionDTO;
import in.tracking.moneymanager.entity.ProfileEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Stream.concat;

/**
 * Service for dashboard data aggregation.
 * Provides summary data for the main dashboard and AI analysis.
 * Results are cached to improve performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TotalSummaryService totalSummaryService;

    private final ProfileService profileService;
    private final ExpenceService expenceService;
    private final IncomeService incomeService;

    /**
     * Get comprehensive dashboard data including totals, recent transactions,
     * and category breakdown (used by AI analysis).
     *
     * @return Map containing all dashboard data
     */
    public Map<String, Object> getDashboardData() {
        ProfileEntity profile = profileService.getCurrentProfile();
        Map<String, Object> returnValue = new LinkedHashMap<>();

        // Get recent transactions
        List<IncomeDTO> latestincomes = incomeService.getLatest5IncomeForCurrentUser();
        List<ExpenceDTO> latestexpence = expenceService.getLatest5ExpenceForCurrentUser();

        // Combine and sort recent transactions
        List<RecentTransactionDTO> RecentTransactions = concat(latestincomes.stream().map(income ->
                        RecentTransactionDTO.builder()
                                .id(income.getId())
                                .profileId(profile.getId())
                                .icon(income.getIcon())
                                .name(income.getName())
                                .amount(income.getAmount())
                                .date(income.getDate())
                                .createdAt(income.getCreatedAt())
                                .updatedAt(income.getUpdatedAt())
                                .type("income")
                                .build()),
                latestexpence.stream().map(expence ->
                        RecentTransactionDTO.builder()
                                .id(expence.getId())
                                .profileId(profile.getId())
                                .icon(expence.getIcon())
                                .name(expence.getName())
                                .amount(expence.getAmount())
                                .date(expence.getDate())
                                .createdAt(expence.getCreatedAt())
                                .updatedAt(expence.getUpdatedAt())
                                .type("expence")
                                .build()))
                .sorted((a, b) -> {
                    int cmp = b.getDate().compareTo(a.getDate());
                    if (cmp == 0 && a.getCreatedAt() != null && b.getCreatedAt() != null) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }
                    return cmp;
                }).collect(Collectors.toList());

        // Calculate totals
        TotalSummaryService.Totals totals = totalSummaryService.getTotals(TotalSummaryService.Period.MONTH);
        BigDecimal totalIncome = totals.getTotalIncome();
        BigDecimal totalExpense = totals.getTotalExpense();
        BigDecimal balance = totals.getBalance();


        // Build category breakdown for AI analysis
        Map<String, BigDecimal> categoryBreakdown = buildCategoryBreakdown();

        // Populate response - using names that match frontend expectations
        returnValue.put("totalBalance", balance);
        returnValue.put("balance", balance);  // Frontend uses "balance"
        returnValue.put("totalIncome", totalIncome);
        returnValue.put("totalExpense", totalExpense);
        returnValue.put("totalExpence", totalExpense);  // Keep for backward compatibility
        returnValue.put("recent5Expence", latestexpence);  // Legacy name
        returnValue.put("recent5Income", latestincomes);   // Legacy name
        returnValue.put("recentExpenses", latestexpence);  // Frontend expects this name
        returnValue.put("recentIncomes", latestincomes);   // Frontend expects this name
        returnValue.put("recentTransactions", RecentTransactions);
        returnValue.put("categoryBreakdown", categoryBreakdown);

        return returnValue;
    }

    /**
     * Get profile ID - useful for other services that need it.
     */
    public Long getCurrentProfileId() {
        return profileService.getCurrentProfile().getId();
    }


    /**
     * Build category breakdown from current month expenses.
     * Used by AI analysis for spending patterns.
     */
    private Map<String, BigDecimal> buildCategoryBreakdown() {
        try {
            List<ExpenceDTO> monthlyExpenses = expenceService.getCurrentMonthExpenceForCurrentUser();

            return monthlyExpenses.stream()
                    .filter(e -> e.getCategoryName() != null)
                    .collect(Collectors.groupingBy(
                            ExpenceDTO::getCategoryName,
                            Collectors.reducing(BigDecimal.ZERO, ExpenceDTO::getAmount, BigDecimal::add)
                    ));
        } catch (Exception e) {
            // Return empty map if there's an error
            return new LinkedHashMap<>();
        }
    }
}

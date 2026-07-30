package in.tracking.moneymanager.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TotalSummaryService {

    public enum Period {
        MONTH, ALL_TIME
    }

    private final IncomeService incomeService;
    private final ExpenceService expenceService;

    public Totals getTotals(Period period) {
        BigDecimal income;
        BigDecimal expense;

        if (period == Period.ALL_TIME) {
            income = incomeService.getTotalIncomeForCurrentUser();
            expense = expenceService.getTotalExpenceForCurrentUser();
        } else {
            LocalDate now = LocalDate.now();
            LocalDate start = now.withDayOfMonth(1);
            LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
            income = incomeService.getTotalIncomeForDateRangeForCurrentUser(start, end);
            expense = expenceService.getTotalExpenceForDateRangeForCurrentUser(start, end);
        }

        BigDecimal balance = income.subtract(expense);
        Double savingsRate = income.compareTo(BigDecimal.ZERO) > 0
                ? balance.multiply(BigDecimal.valueOf(100))
                .divide(income, 2, RoundingMode.HALF_UP)
                .doubleValue()
                : 0.0;

        return Totals.builder()
                .totalIncome(income)
                .totalExpense(expense)
                .balance(balance)
                .savingsRate(savingsRate)
                .period(period.name())
                .build();
    }

    @Data
    @Builder
    public static class Totals {
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal balance;
        private Double savingsRate;
        private String period;
    }
}

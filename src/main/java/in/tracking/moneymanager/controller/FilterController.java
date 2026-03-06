package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.FilterDTO;
import in.tracking.moneymanager.service.ExpenceService;
import in.tracking.moneymanager.service.IncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/filter")
public class FilterController {

    private final IncomeService incomeService;
    private final ExpenceService expenceService;

    // PostgreSQL-compatible minimum date (LocalDate.MIN is not supported by PostgreSQL)
    private static final LocalDate PG_MIN_DATE = LocalDate.of(2026, 1, 1);

    @PostMapping
    public ResponseEntity<?> filterTransactions(@RequestBody FilterDTO filter){
        LocalDate startDate = filter.getStartDate() != null ? filter.getStartDate() : PG_MIN_DATE;
        LocalDate endDate = filter.getEndDate() != null ? filter.getEndDate() : LocalDate.now();
        String keyword = filter.getKeyword() != null ? filter.getKeyword() : "";
        String sortField = filter.getSortField() != null ? filter.getSortField() : "date";
        Sort.Direction direction = "desc".equalsIgnoreCase(filter.getSortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortField);
        if("income".equalsIgnoreCase(filter.getType())){
            return ResponseEntity.ok(incomeService.filterIncomes(startDate, endDate, keyword, sort));
        }else if("expence".equalsIgnoreCase(filter.getType())){
            return ResponseEntity.ok(expenceService.filterExpences(startDate, endDate, keyword, sort));
        }else {
            return ResponseEntity.badRequest().body("Invalid type, Must be either income or expence ");
        }
    }
}

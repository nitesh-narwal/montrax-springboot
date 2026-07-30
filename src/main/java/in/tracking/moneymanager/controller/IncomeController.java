package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.IncomeDTO;
import in.tracking.moneymanager.dto.PagedResponse;
import in.tracking.moneymanager.service.IncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/incomes")
public class IncomeController {

    private final IncomeService incomeService;

    @PostMapping
    public ResponseEntity<IncomeDTO> addIncome(@RequestBody IncomeDTO dto) {
        IncomeDTO saved = incomeService.addIncome(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<IncomeDTO>> getIncomes() {
        return ResponseEntity.ok(incomeService.getCurrentMonthIncomeForCurrentUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable long id){
        incomeService.deleteIncome(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<IncomeDTO>> getAllIncomeDesc() {
        return ResponseEntity.ok(incomeService.getAllIncomeForCurrentUserOrderByDateDesc());
    }

    // Paginated income history: GET /incomes/paged?page=0&size=20&sort=date,desc
    @GetMapping("/paged")
    public ResponseEntity<PagedResponse<IncomeDTO>> getIncomesPaged(
            @PageableDefault(size = 20, sort = "date") Pageable pageable) {
        return ResponseEntity.ok(incomeService.getIncomesPaginated(pageable));
    }
}

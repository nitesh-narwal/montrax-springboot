package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.service.TotalSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/totals")
@RequiredArgsConstructor
public class TotalsController {


    private final TotalSummaryService totalSummaryService;

    @GetMapping
    public ResponseEntity<TotalSummaryService.Totals> getTotals(
            @RequestParam(defaultValue = "MONTH") TotalSummaryService.Period period) {
        return ResponseEntity.ok(totalSummaryService.getTotals(period));
    }
}

package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.CommonResponse;
import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.dto.PagedResponse;
import in.tracking.moneymanager.service.ExpenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expences")
public class ExpenceController {

    private final ExpenceService expenceService;

     // Endpoint to add a new expence
     @PostMapping
     public ResponseEntity<CommonResponse<ExpenceDTO>> addExpence(@RequestBody ExpenceDTO expenceDTO) {
         ExpenceDTO createdExpence = expenceService.addExpence(expenceDTO);
         return ResponseEntity.status(HttpStatus.CREATED)
                 .body(CommonResponse.success(createdExpence, "Expense created successfully"));
     }

    //Endpoint to get all expence
    @GetMapping
    public ResponseEntity<List<ExpenceDTO>> getExpences() {
        return ResponseEntity.ok(expenceService.getCurrentMonthExpenceForCurrentUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpence(@PathVariable long id){
        expenceService.deleteExpence(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<ExpenceDTO>> getAllExpenceDesc() {
        return ResponseEntity.ok(expenceService.getAllExpenceForCurrentUserOrderByDateDesc());
    }

    // Paginated expense history: GET /expences/paged?page=0&size=20&sort=date,desc
    @GetMapping("/paged")
    public ResponseEntity<PagedResponse<ExpenceDTO>> getExpencesPaged(
            @PageableDefault(size = 20, sort = "date") Pageable pageable) {
        return ResponseEntity.ok(expenceService.getExpencesPaginated(pageable));
    }

}

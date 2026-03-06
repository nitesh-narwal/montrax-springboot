package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.service.ExpenceService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<ExpenceDTO> addExpence(@RequestBody ExpenceDTO expenceDTO) {
        ExpenceDTO createdExpence = expenceService.addExpence(expenceDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdExpence);
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
}

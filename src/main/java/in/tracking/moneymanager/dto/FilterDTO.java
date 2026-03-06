package in.tracking.moneymanager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FilterDTO {

    private String type; // "income" or "expense"

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String keyword;
    private String sortField; // "date", "amount", "name"
    private String sortOrder; // "asc" or "desc"

}

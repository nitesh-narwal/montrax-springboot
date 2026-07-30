package in.tracking.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SavingsGoalDTO {
    private Long id;
    private String name;
    private String icon;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal remainingAmount;
    private Double percentageProgress;
    private LocalDate targetDate;
    private String status;
    private Boolean isCompleted;
    private LocalDateTime createdAt;
}

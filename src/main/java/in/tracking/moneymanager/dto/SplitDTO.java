package in.tracking.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SplitDTO {

    private Long id;
    private String participantName;
    private BigDecimal shareAmount;
    private Boolean isSettled;
    private LocalDateTime settledAt;
}

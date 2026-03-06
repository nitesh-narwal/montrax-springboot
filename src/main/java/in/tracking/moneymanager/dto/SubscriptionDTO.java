package in.tracking.moneymanager.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * DTO for user's current subscription status.
 * Includes plan details and usage statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDTO {

    private Long id;

    // Plan info
    private String planType;      // FREE, BASIC, PREMIUM
    private String planName;      // Human-readable name

    // Validity
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;        // ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD
    private Boolean autoRenew;
    private Integer daysRemaining;

    // Usage tracking
    private Integer aiQueriesUsed;
    private Integer aiQueriesLimit;
    private Integer csvImportsUsed;
    private Integer csvImportsLimit;
}


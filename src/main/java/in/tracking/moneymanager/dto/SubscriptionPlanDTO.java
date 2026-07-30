package in.tracking.moneymanager.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for subscription plan information.
 * Used when displaying available plans to users.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanDTO {

    private Long id;

    // Plan name: FREE, BASIC, PREMIUM
    private String name;

    // Human-readable description
    private String description;

    // Price in INR
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;

    // Plan limits
    private Integer maxCategories;
    private Integer maxBankImports;
    private Integer aiQueriesPerMonth;

    // Data retention period in months (0 = unlimited)
    private Integer dataRetentionMonths;
}


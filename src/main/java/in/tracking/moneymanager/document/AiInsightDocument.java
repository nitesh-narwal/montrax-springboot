package in.tracking.moneymanager.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB document for storing AI-generated insights.
 * Uses flexible schema for varying AI response structures.
 *
 * Insight types: SPENDING_ANALYSIS, SAVINGS_STRATEGY, FINANCIAL_HEALTH, etc.
 * Priority: HIGH, MEDIUM, LOW
 *
 * Documents auto-expire after 7 days via TTL index.
 */
@Document(collection = "ai_insights")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInsightDocument {

    @Id
    private String id;

    // User's profile ID for filtering
    @Indexed
    private Long profileId;

    // Type of insight: SPENDING_ANALYSIS, SAVINGS_STRATEGY, etc.
    private String insightType;

    // Human-readable title for display
    private String title;

    // Summary description
    private String description;

    // Flexible data storage for AI response (JSON-like structure)
    private Map<String, Object> data;

    // Estimated savings if recommendations are followed (in INR)
    private BigDecimal potentialSavings;

    // Priority level: HIGH, MEDIUM, LOW
    private String priority;

    // User interaction tracking
    @Builder.Default
    private Boolean isRead = false;

    @Builder.Default
    private Boolean isActionTaken = false;

    // Timestamps
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // TTL index field - document expires 7 days after this time
    @Indexed(expireAfter = "7d")
    private LocalDateTime expiresAt;
}


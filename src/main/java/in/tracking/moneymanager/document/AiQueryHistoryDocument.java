package in.tracking.moneymanager.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MongoDB document for tracking AI query history.
 * Used for rate limiting (queries per month) and analytics.
 *
 * Query types: ANALYZE, ASK, STRATEGY, HEALTH
 */
@Document(collection = "ai_query_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiQueryHistoryDocument {

    @Id
    private String id;

    // User's profile ID
    @Indexed
    private Long profileId;

    // Type of query: ANALYZE, ASK, STRATEGY, HEALTH
    private String queryType;

    // User's original question (for ASK type queries)
    private String userQuery;

    // Full AI response text
    private String aiResponse;

    // Financial context sent to AI (snapshot of user's data)
    private Map<String, Object> context;

    // Token usage for tracking API costs
    private Integer tokensUsed;

    // Response time in milliseconds (for performance monitoring)
    private Long responseTimeMs;

    // AI model used (e.g., gemini-1.5-flash)
    private String model;

    // Timestamp for rate limiting calculations
    @Indexed
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}


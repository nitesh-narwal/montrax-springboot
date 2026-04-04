package in.tracking.moneymanager.repository.mongo;

import in.tracking.moneymanager.document.AiInsightDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for AI insights.
 * Stores AI-generated financial insights for users.
 */
@Repository
public interface AiInsightRepository extends MongoRepository<AiInsightDocument, String> {

    // Get all insights for a user, newest first
    List<AiInsightDocument> findByProfileIdOrderByCreatedAtDesc(Long profileId);

    // Get unread insights for notification badge
    List<AiInsightDocument> findByProfileIdAndIsReadFalse(Long profileId);

    // Get insights by priority level
    List<AiInsightDocument> findByProfileIdAndPriority(Long profileId, String priority);

    // Get insights by type
    List<AiInsightDocument> findByProfileIdAndInsightType(Long profileId, String insightType);

    // Get the latest insight by type (for caching)
    Optional<AiInsightDocument> findTopByProfileIdAndInsightTypeOrderByCreatedAtDesc(Long profileId, String insightType);

    // Get insights created after a certain time
    List<AiInsightDocument> findByProfileIdAndInsightTypeAndCreatedAtAfter(Long profileId, String insightType, LocalDateTime after);

    // Delete old insights by type (for cleanup)
    long deleteByProfileIdAndInsightTypeAndCreatedAtBefore(Long profileId, String insightType, LocalDateTime before);
}


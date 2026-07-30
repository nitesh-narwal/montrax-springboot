package in.tracking.moneymanager.repository.mongo;

import in.tracking.moneymanager.document.AiQueryHistoryDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB repository for AI query history.
 * Used for tracking usage and rate limiting.
 */
@Repository
public interface AiQueryHistoryRepository extends MongoRepository<AiQueryHistoryDocument, String> {

    // Get query history for a user with pagination
    List<AiQueryHistoryDocument> findByProfileIdOrderByCreatedAtDesc(Long profileId, Pageable pageable);

    // Count queries since a date (for monthly rate limiting)
    Long countByProfileIdAndCreatedAtAfter(Long profileId, LocalDateTime after);

    // Get all queries of a specific type
    List<AiQueryHistoryDocument> findByProfileIdAndQueryType(Long profileId, String queryType);
}


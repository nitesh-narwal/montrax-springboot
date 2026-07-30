package in.tracking.moneymanager.repository.mongo;

import in.tracking.moneymanager.document.AuditLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLogDocument, String> {

    Page<AuditLogDocument> findByUserEmailOrderByTimestampDesc(String userEmail, Pageable pageable);

    Page<AuditLogDocument> findAllByOrderByTimestampDesc(Pageable pageable);
}

package in.tracking.moneymanager.service.event;

import in.tracking.moneymanager.config.KafkaConfig;
import in.tracking.moneymanager.document.AuditLogDocument;
import in.tracking.moneymanager.dto.event.AuditEvent;
import in.tracking.moneymanager.repository.mongo.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Persists audit events to MongoDB. No-ops when MongoDB isn't configured
 * (same optional-dependency pattern as AppCacheService) so the consumer
 * doesn't fail the whole app when Mongo is absent.
 */
@Component
@Slf4j
public class AuditEventConsumer {

    @Autowired(required = false)
    private AuditLogRepository auditLogRepository;

    @KafkaListener(topics = KafkaConfig.AUDIT_TOPIC, groupId = "${kafka.consumer.group-id}")
    public void handleAuditEvent(AuditEvent event) {
        if (auditLogRepository == null) {
            return;
        }

        try {
            auditLogRepository.save(AuditLogDocument.builder()
                    .userEmail(event.getUserEmail())
                    .method(event.getMethod())
                    .path(event.getPath())
                    .statusCode(event.getStatusCode())
                    .ipAddress(event.getIpAddress())
                    .durationMs(event.getDurationMs())
                    .timestamp(event.getTimestamp())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist audit event: {}", e.getMessage());
        }
    }
}

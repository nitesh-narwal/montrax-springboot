package in.tracking.moneymanager.service.event;

import in.tracking.moneymanager.config.KafkaConfig;
import in.tracking.moneymanager.dto.event.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AuditEvent event) {
        try {
            kafkaTemplate.send(KafkaConfig.AUDIT_TOPIC, event.getUserEmail(), event);
        } catch (Exception e) {
            log.error("Failed to publish audit event for user {}: {}", event.getUserEmail(), e.getMessage());
        }
    }
}

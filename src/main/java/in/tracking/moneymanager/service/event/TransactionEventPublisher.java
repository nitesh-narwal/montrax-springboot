package in.tracking.moneymanager.service.event;

import in.tracking.moneymanager.config.KafkaConfig;
import in.tracking.moneymanager.dto.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes expense/income CRUD events for downstream analytics consumers.
 * Best-effort: publish failures are logged, never block the originating transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TransactionEvent event) {
        try {
            kafkaTemplate.send(KafkaConfig.TRANSACTIONS_TOPIC, String.valueOf(event.getProfileId()), event);
        } catch (Exception e) {
            log.error("Failed to publish transaction event for profile {}: {}", event.getProfileId(), e.getMessage());
        }
    }
}

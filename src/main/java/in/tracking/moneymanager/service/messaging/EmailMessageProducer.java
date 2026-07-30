package in.tracking.moneymanager.service.messaging;

import in.tracking.moneymanager.config.RabbitMQConfig;
import in.tracking.moneymanager.dto.message.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes emails to RabbitMQ so callers don't block on SMTP round-trips.
 * Falls back to nothing on publish failure - callers should not fail their
 * primary transaction (e.g. registration) just because the mail queue is down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String to, String subject, String body) {
        try {
            EmailMessage message = EmailMessage.builder()
                    .to(to)
                    .subject(subject)
                    .body(body)
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.EMAIL_ROUTING_KEY, message);
        } catch (Exception e) {
            log.error("Failed to publish email message to: {}. Error: {}", to, e.getMessage());
        }
    }
}

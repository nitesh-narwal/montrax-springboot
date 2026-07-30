package in.tracking.moneymanager.service.messaging;

import in.tracking.moneymanager.config.RabbitMQConfig;
import in.tracking.moneymanager.dto.message.EmailMessage;
import in.tracking.moneymanager.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes queued emails. Throws on failure so spring-rabbit's retry
 * interceptor (3 attempts, see application.properties) can retry before
 * the message is dead-lettered to email.queue.dlq.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailMessageConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handleEmail(EmailMessage message) {
        emailService.sendEmail(message.getTo(), message.getSubject(), message.getBody());
        log.info("Async email delivered to: {}", message.getTo());
    }
}

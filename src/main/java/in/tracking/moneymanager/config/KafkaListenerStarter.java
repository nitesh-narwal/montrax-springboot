package in.tracking.moneymanager.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

/**
 * Starts Kafka consumers manually once the app is already up, instead of
 * during context refresh (see KafkaConfig#kafkaListenerContainerFactory,
 * autoStartup=false). A broker that's briefly unreachable, or a DNS hiccup
 * resolving a managed broker's hostname, must not take the whole application
 * down with it - Kafka here is async audit/event streaming, not core
 * functionality. Producer sends are separately best-effort/try-caught, so
 * the app is fully usable even if Kafka never comes up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerStarter {

    private final KafkaListenerEndpointRegistry registry;

    @EventListener(ApplicationReadyEvent.class)
    public void startListeners() {
        try {
            registry.start();
            log.info("Kafka listeners started");
        } catch (Exception e) {
            log.error("Kafka listeners failed to start - app continues without event consumption: {}", e.getMessage());
        }
    }
}

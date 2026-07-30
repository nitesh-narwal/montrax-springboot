package in.tracking.moneymanager.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Event streaming topology. Topics are explicitly provisioned by KafkaAdmin
 * below on startup - the broker's auto.create.topics.enable can't be relied
 * on (often off by default), and that's what was causing producer sends to
 * time out with UNKNOWN_TOPIC_OR_PARTITION.
 *
 * The listener container factory has autoStartup disabled - KafkaListenerStarter
 * starts it after the app is already up, so a broker that's briefly unreachable
 * (or a DNS hiccup resolving a managed broker's hostname) can't crash the whole
 * application. Kafka backs async audit/event streaming here, not core functionality.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TRANSACTIONS_TOPIC = "moneymanager.transactions";
    public static final String AUDIT_TOPIC = "moneymanager.audit";
    public static final String ANALYTICS_TOPIC = "moneymanager.analytics";

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:moneymanager-group}")
    private String groupId;

    // SASL_SSL auth for managed brokers (Aiven, Confluent Cloud, ...). Left blank
    // for a plain local/dev broker, in which case no security config is applied.
    @Value("${kafka.sasl.username:}")
    private String saslUsername;

    @Value("${kafka.sasl.password:}")
    private String saslPassword;

    @Value("${kafka.sasl.mechanism:PLAIN}")
    private String saslMechanism;

    /**
     * Anything under kafka.properties.* (truststore location/type, endpoint
     * identification, connection timeouts/backoff, ...) is passed straight
     * through to the Kafka client - same convention Spring Boot itself uses
     * for spring.kafka.properties.*. Add more tuning there without touching Java.
     */
    @Bean
    @ConfigurationProperties(prefix = "kafka.properties")
    public Map<String, String> kafkaClientProperties() {
        return new HashMap<>();
    }

    @Bean
    public KafkaAdmin kafkaAdmin(Map<String, String> kafkaClientProperties) {
        Map<String, Object> config = new HashMap<>(kafkaClientProperties);
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        applySecurityConfig(config);
        return new KafkaAdmin(config);
    }

    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(TRANSACTIONS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic auditTopic() {
        return TopicBuilder.name(AUDIT_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic analyticsTopic() {
        return TopicBuilder.name(ANALYTICS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(Map<String, String> kafkaClientProperties) {
        Map<String, Object> config = new HashMap<>(kafkaClientProperties);
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        applySecurityConfig(config);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(Map<String, String> kafkaClientProperties) {
        Map<String, Object> config = new HashMap<>(kafkaClientProperties);
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "in.tracking.moneymanager.dto.event");
        applySecurityConfig(config);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    // Explicit security decision always wins over whatever kafka.properties.* supplied,
    // and is auto-detected from whether credentials are present - no extra env var needed.
    private void applySecurityConfig(Map<String, Object> config) {
        if (saslUsername == null || saslUsername.isBlank()) {
            return; // plain local/dev broker, nothing to configure
        }
        config.put("security.protocol", "SASL_SSL");
        config.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        String loginModule = saslMechanism.startsWith("SCRAM")
                ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                : "org.apache.kafka.common.security.plain.PlainLoginModule";
        config.put(SaslConfigs.SASL_JAAS_CONFIG,
                loginModule + " required username=\"" + saslUsername + "\" password=\"" + saslPassword + "\";");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(false);
        return factory;
    }
}

package in.tracking.moneymanager.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

/**
 * Async processing topology: one topic exchange for app events, each queue
 * dead-letters into a shared DLX so failed messages (after retry) land in
 * an inspectable *.dlq instead of being silently dropped.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "moneymanager.exchange";
    public static final String DLX = "moneymanager.dlx";

    public static final String EMAIL_QUEUE = "email.queue";
    public static final String EMAIL_ROUTING_KEY = "email.send";

    public static final String CSV_QUEUE = "csv.queue";
    public static final String CSV_ROUTING_KEY = "csv.import";

    public static final String SUBSCRIPTION_QUEUE = "subscription.queue";
    public static final String SUBSCRIPTION_ROUTING_KEY = "subscription.event";

    // Full AMQP connection URI (scheme+user+pass+host+port+vhost all in one).
    // NOTE: plain spring.rabbitmq.host/port/username/password properties are NOT
    // used here - this project's Spring Boot version has no spring.rabbitmq.uri
    // property at all, so the ConnectionFactory is built directly from the URI
    // via the RabbitMQ client's own setUri(), which also auto-enables TLS for
    // an amqps:// scheme (e.g. CloudAMQP).
    @Value("${rabbitmq.uri:amqp://guest:guest@localhost:5672}")
    private String rabbitUri;

    @Bean
    public ConnectionFactory connectionFactory() throws Exception {
        com.rabbitmq.client.ConnectionFactory rabbitClientFactory = new com.rabbitmq.client.ConnectionFactory();
        rabbitClientFactory.setUri(rabbitUri);
        if (rabbitUri.startsWith("amqps://")) {
            // setUri() enables TLS for amqps:// via a trust-everything manager by
            // default (accepts any certificate). Replace it with the JVM's real
            // trust store so the broker's certificate is actually verified.
            rabbitClientFactory.useSslProtocol(SSLContext.getDefault());
        }
        return new CachingConnectionFactory(rabbitClientFactory);
    }

    @Bean
    public TopicExchange moneyManagerExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", EMAIL_QUEUE + ".dlq")
                .build();
    }

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(EMAIL_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue csvQueue() {
        return QueueBuilder.durable(CSV_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", CSV_QUEUE + ".dlq")
                .build();
    }

    @Bean
    public Queue csvDlq() {
        return QueueBuilder.durable(CSV_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue subscriptionQueue() {
        return QueueBuilder.durable(SUBSCRIPTION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", SUBSCRIPTION_QUEUE + ".dlq")
                .build();
    }

    @Bean
    public Queue subscriptionDlq() {
        return QueueBuilder.durable(SUBSCRIPTION_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange moneyManagerExchange) {
        return BindingBuilder.bind(emailQueue).to(moneyManagerExchange).with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding csvBinding(Queue csvQueue, TopicExchange moneyManagerExchange) {
        return BindingBuilder.bind(csvQueue).to(moneyManagerExchange).with(CSV_ROUTING_KEY);
    }

    @Bean
    public Binding subscriptionBinding(Queue subscriptionQueue, TopicExchange moneyManagerExchange) {
        return BindingBuilder.bind(subscriptionQueue).to(moneyManagerExchange).with(SUBSCRIPTION_ROUTING_KEY);
    }

    @Bean
    public Binding emailDlqBinding(Queue emailDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(emailDlq).to(deadLetterExchange).with(EMAIL_QUEUE + ".dlq");
    }

    @Bean
    public Binding csvDlqBinding(Queue csvDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(csvDlq).to(deadLetterExchange).with(CSV_QUEUE + ".dlq");
    }

    @Bean
    public Binding subscriptionDlqBinding(Queue subscriptionDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(subscriptionDlq).to(deadLetterExchange).with(SUBSCRIPTION_QUEUE + ".dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}

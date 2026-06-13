package com.paymentplatform.transaction.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.Map;

/**
 * KafkaConfig — Transaction Service
 *
 * Consumer: listens for PaymentProcessedEvent (Block 4) to auto-create transaction records
 * Producer: publishes TransactionCreatedEvent and ReconciliationEvent (Block 4)
 *
 * Adyen interview: "ACKS=all + idempotent producer = exactly-once Kafka semantics.
 * MAX_IN_FLIGHT=1 prevents reordering when retries occur."
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Consumer ──────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,            "transaction-service",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,   "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,  false,  // manual ack for reliability
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG,    50
        ));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);  // 3 threads = 3 partitions consumed in parallel
        return factory;
    }

    // ── Producer ──────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,         bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,      StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,    StringSerializer.class,
                ProducerConfig.ACKS_CONFIG,                      "all",       // wait for all ISR replicas
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,        true,        // exactly-once
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1,      // prevent reordering
                ProducerConfig.RETRIES_CONFIG,                   3,
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG,          1000
        ));
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
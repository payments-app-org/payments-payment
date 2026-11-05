package com.paymentsapp.payment.messaging;

import com.paymentsapp.payment.payment.PaymentStore;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    // Имя, число партиций и реплик — из конфигурации (messaging.payment-events.*)
    @Bean
    NewTopic paymentEventsTopic(@Value("${messaging.payment-events.topic}") String name,
                        @Value("${messaging.payment-events.partitions}") int partitions,
                        @Value("${messaging.payment-events.replicas}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}

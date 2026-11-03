package com.paymentsapp.payment.outbox;

import com.paymentsapp.payment.payment.PaymentStore;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(PaymentStore.TOPIC).partitions(3).replicas(1).build();
    }
}

package com.flashsale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${flashsale.kafka.topic.booking}")
    private String bookingTopic;

    @Value("${flashsale.kafka.topic.order}")
    private String orderTopic;

    @Bean
    public NewTopic bookingTopic() {
        return TopicBuilder.name(bookingTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(orderTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.UsageEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfiguration {

    @Bean
    NewTopic usageEventsTopic() {
        return TopicBuilder.name(UsageEventPublisher.USAGE_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.UsageEvent;
import java.util.Map;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
class KafkaProducerConfiguration {

    @Bean
    ProducerFactory<String, UsageEvent> usageEventProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, UsageEvent> usageEventKafkaTemplate(
            ProducerFactory<String, UsageEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
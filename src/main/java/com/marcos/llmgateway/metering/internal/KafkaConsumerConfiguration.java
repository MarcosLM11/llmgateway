package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.UsageEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@EnableKafka
class KafkaConsumerConfiguration {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, UsageEvent> kafkaListenerContainerFactory(ConsumerFactory<String, UsageEvent> consumerFactory) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, UsageEvent>();
        factory.setConsumerFactory(consumerFactory);

        // Clave: habilita ack manual para que Spring inyecte Acknowledgment en el listener
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // alternativa más inmediata:
        // factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}

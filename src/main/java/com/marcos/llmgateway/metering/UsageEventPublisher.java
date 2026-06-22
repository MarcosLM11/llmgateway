package com.marcos.llmgateway.metering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class UsageEventPublisher {
    public static final String USAGE_EVENTS_TOPIC = "usage-events";

    private static final Logger log = LoggerFactory.getLogger(UsageEventPublisher.class);
    private final KafkaTemplate<String, UsageEvent> kafkaTemplate;

    public UsageEventPublisher(KafkaTemplate<String, UsageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UsageEvent event) {
        kafkaTemplate.send(USAGE_EVENTS_TOPIC, event.tenantId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UsageEvent for tenant={}, requestId={}",
                                event.tenantId(), event.requestId(), ex);
                    } else {
                        log.debug("Published UsageEvent for tenant={}, requestId={}, partition={}, offset={}",
                                event.tenantId(), event.requestId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}

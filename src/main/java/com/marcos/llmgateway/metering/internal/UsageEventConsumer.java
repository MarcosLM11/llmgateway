package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.UsageEvent;
import com.marcos.llmgateway.metering.UsageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
class UsageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventConsumer.class);
    private final UsageEventRepository repository;

    UsageEventConsumer(UsageEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = UsageEventPublisher.USAGE_EVENTS_TOPIC,
            groupId = "llmgateway-metering"
    )
    void consume(UsageEvent event, Acknowledgment ack) {
        try {
            var inserted = repository.insertIfAbsent(event);
            if (inserted) {
                log.info("Persisted UsageEvent requestId={} tenant={}", event.requestId(), event.tenantId());
            } else {
                log.debug("Duplicate UsageEvent ignored requestId={}", event.requestId());
            }
            ack.acknowledge();
        } catch (RuntimeException e) {
            log.error("Failed to persist UsageEvent requestId={}", event.requestId(), e);
            // No acknowledge → Kafka redeliver
            throw e;
        }
    }
}

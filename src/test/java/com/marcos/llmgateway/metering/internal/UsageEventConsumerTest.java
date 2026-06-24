package com.marcos.llmgateway.metering.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.marcos.llmgateway.metering.UsageEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;

class UsageEventConsumerTest {

    private UsageEventRepository repository;
    private Acknowledgment acknowledgment;
    private UsageEventConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(UsageEventRepository.class);
        acknowledgment = mock(Acknowledgment.class);
        consumer = new UsageEventConsumer(repository);
    }

    @Test
    void consume_success_acknowledgesExactlyOnce() {
        UsageEvent event = usageEvent();
        when(repository.insertIfAbsent(event)).thenReturn(true);

        consumer.consume(event, acknowledgment);

        verify(acknowledgment).acknowledge();
        verifyNoMoreInteractions(acknowledgment);
    }

    @Test
    void consume_duplicate_acknowledgesExactlyOnce() {
        UsageEvent event = usageEvent();
        when(repository.insertIfAbsent(event)).thenReturn(false);

        consumer.consume(event, acknowledgment);

        verify(acknowledgment).acknowledge();
        verifyNoMoreInteractions(acknowledgment);
    }

    @Test
    void consume_repositoryFailure_doesNotAcknowledgeAndRethrows() {
        UsageEvent event = usageEvent();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db down");
        when(repository.insertIfAbsent(event)).thenThrow(failure);

        assertThatThrownBy(() -> consumer.consume(event, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to persist UsageEvent requestId=req-123")
                .hasCause(failure);

        verifyNoInteractions(acknowledgment);
    }

    private UsageEvent usageEvent() {
        return new UsageEvent(
                "req-123",
                "tenant-a",
                "gpt-4o-mini",
                "provider-a",
                10,
                20,
                false,
                150,
                Instant.parse("2026-06-24T12:00:00Z")
        );
    }
}


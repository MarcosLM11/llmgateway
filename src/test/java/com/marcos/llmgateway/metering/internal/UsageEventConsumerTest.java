package com.marcos.llmgateway.metering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.marcos.llmgateway.metering.UsageEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;

class UsageEventConsumerTest {

    private UsageEventRepository repository;
    private PricingRepository pricingRepository;
    private CostCalculator costCalculator;
    private Acknowledgment acknowledgment;
    private UsageEventConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(UsageEventRepository.class);
        pricingRepository = mock(PricingRepository.class);
        costCalculator = mock(CostCalculator.class);
        acknowledgment = mock(Acknowledgment.class);
        consumer = new UsageEventConsumer(repository, pricingRepository, costCalculator);
    }

    @Test
    void consume_cacheHit_persistsZeroCostWithoutLookup() {
        var event = usageEvent(true);
        when(repository.insertIfAbsent(eq(event), eq(BigDecimal.ZERO))).thenReturn(true);

        consumer.consume(event, acknowledgment);

        verify(repository).insertIfAbsent(event, BigDecimal.ZERO);
        verifyNoInteractions(pricingRepository, costCalculator);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_cacheMissWithPricingRule_persistsCalculatedCost() {
        var event = usageEvent(false);
        var rule = new PricingRule(new BigDecimal("0.15"), new BigDecimal("0.60"));
        var expectedCost = new BigDecimal("0.00001500");
        when(pricingRepository.findRule("mock", "gpt-4o-mini")).thenReturn(Optional.of(rule));
        when(costCalculator.calculate(rule, 10, 20)).thenReturn(expectedCost);
        when(repository.insertIfAbsent(eq(event), eq(expectedCost))).thenReturn(true);

        consumer.consume(event, acknowledgment);

        verify(repository).insertIfAbsent(event, expectedCost);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_cacheMissWithNoPricingRule_persistsNullCost() {
        var event = usageEvent(false);
        when(pricingRepository.findRule("mock", "gpt-4o-mini")).thenReturn(Optional.empty());
        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        when(repository.insertIfAbsent(eq(event), costCaptor.capture())).thenReturn(true);
        consumer.consume(event, acknowledgment);
        assertThat(costCaptor.getValue()).isNull();
        verifyNoInteractions(costCalculator);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_duplicate_acknowledgesWithoutError() {
        var event = usageEvent(true);
        when(repository.insertIfAbsent(eq(event), any())).thenReturn(false);
        consumer.consume(event, acknowledgment);
        verify(acknowledgment).acknowledge();
        verifyNoMoreInteractions(acknowledgment);
    }

    @Test
    void consume_repositoryFailure_doesNotAcknowledgeAndRethrows() {
        var event = usageEvent(true);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db down");
        when(repository.insertIfAbsent(eq(event), any())).thenThrow(failure);
        assertThatThrownBy(() -> consumer.consume(event, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to persist UsageEvent requestId=req-123")
                .hasCause(failure);
        verifyNoInteractions(acknowledgment);
    }

    private UsageEvent usageEvent(boolean cacheHit) {
        return new UsageEvent(
                "req-123",
                "tenant-a",
                "gpt-4o-mini",
                "mock",
                10,
                20,
                cacheHit,
                150,
                Instant.parse("2026-06-24T12:00:00Z")
        );
    }
}
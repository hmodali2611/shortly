package com.example.shortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RedisCircuitBreakerTest {

	@Test
	void allowsSingleRecoveryProbeAndClosesAfterSuccess() {
		Clock clock = mock(Clock.class);
		Instant now = Instant.parse("2026-08-27T12:00:00Z");
		when(clock.millis()).thenReturn(now.toEpochMilli(), now.plusSeconds(4).toEpochMilli(),
				now.plusSeconds(5).toEpochMilli());
		RedisCircuitBreaker circuitBreaker = new RedisCircuitBreaker(clock, 2, Duration.ofSeconds(5));

		circuitBreaker.recordFailure();
		circuitBreaker.recordFailure();

		assertThat(circuitBreaker.tryAcquire()).isFalse();
		assertThat(circuitBreaker.tryAcquire()).isTrue();
		assertThat(circuitBreaker.tryAcquire()).isFalse();

		circuitBreaker.recordSuccess();

		assertThat(circuitBreaker.tryAcquire()).isTrue();
	}
}
package com.example.shortener.redirect;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class RedisCircuitBreaker {

	private static final long CLOSED = 0;
	private static final long HALF_OPEN = -1;
	private final AtomicInteger consecutiveFailures = new AtomicInteger();
	private final AtomicLong openUntilEpochMilli = new AtomicLong(CLOSED);
	private final Clock clock;
	private final int failureThreshold;
	private final Duration openDuration;

	RedisCircuitBreaker(Clock clock, @Value("${app.cache.circuit-breaker.failure-threshold}") int failureThreshold,
			@Value("${app.cache.circuit-breaker.open-duration}") Duration openDuration) {
		if (failureThreshold < 1 || openDuration.isNegative() || openDuration.isZero()) {
			throw new IllegalArgumentException("Redis circuit-breaker settings must be positive");
		}
		this.clock = clock;
		this.failureThreshold = failureThreshold;
		this.openDuration = openDuration;
	}

	boolean tryAcquire() {
		long state = openUntilEpochMilli.get();
		if (state == CLOSED) {
			return true;
		}
		if (state == HALF_OPEN || clock.millis() < state) {
			return false;
		}
		return openUntilEpochMilli.compareAndSet(state, HALF_OPEN);
	}

	void recordSuccess() {
		consecutiveFailures.set(0);
		openUntilEpochMilli.set(CLOSED);
	}

	void recordFailure() {
		if (openUntilEpochMilli.get() == HALF_OPEN || consecutiveFailures.incrementAndGet() >= failureThreshold) {
			consecutiveFailures.set(0);
			openUntilEpochMilli.set(clock.millis() + openDuration.toMillis());
		}
	}
}
package com.example.shortener.redirect;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards Redis cache calls with a three-state breaker (closed/open/half-open).
 * Calls are infrequent enough per request that a synchronized critical section
 * costs nothing measurable over a lock-free implementation, so state is kept as
 * plain fields behind {@code synchronized} rather than packed into atomics.
 */
@Component
class RedisCircuitBreaker {

	private enum State {
		CLOSED, OPEN, HALF_OPEN
	}

	private final Clock clock;
	private final int failureThreshold;
	private final Duration openDuration;

	private State state = State.CLOSED;
	private int consecutiveFailures;
	private long openUntilEpochMilli;

	RedisCircuitBreaker(Clock clock, @Value("${app.cache.circuit-breaker.failure-threshold}") int failureThreshold,
			@Value("${app.cache.circuit-breaker.open-duration}") Duration openDuration) {
		if (failureThreshold < 1 || openDuration.isNegative() || openDuration.isZero()) {
			throw new IllegalArgumentException("Redis circuit-breaker settings must be positive");
		}
		this.clock = clock;
		this.failureThreshold = failureThreshold;
		this.openDuration = openDuration;
	}

	synchronized boolean tryAcquire() {
		if (state == State.CLOSED) {
			return true;
		}
		if (state == State.HALF_OPEN) {
			return false;
		}
		if (clock.millis() < openUntilEpochMilli) {
			return false;
		}
		state = State.HALF_OPEN;
		return true;
	}

	synchronized void recordSuccess() {
		state = State.CLOSED;
		consecutiveFailures = 0;
	}

	synchronized void recordFailure() {
		if (state == State.HALF_OPEN || ++consecutiveFailures >= failureThreshold) {
			state = State.OPEN;
			consecutiveFailures = 0;
			openUntilEpochMilli = clock.millis() + openDuration.toMillis();
		}
	}
}
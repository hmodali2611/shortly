package com.example.shortener.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.shortener.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimiterTest {

	@Test
	void allowsCreatesAtOrUnderTheLimit() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.increment(anyString())).thenReturn(5L);
		RateLimiter limiter = new RateLimiter(redis, 5);

		assertThatCode(() -> limiter.checkCreate("client-a")).doesNotThrowAnyException();
	}

	@Test
	void rejectsCreatesOverTheLimitWith429() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.increment(anyString())).thenReturn(6L);
		RateLimiter limiter = new RateLimiter(redis, 5);

		assertThatThrownBy(() -> limiter.checkCreate("client-a")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(429);
	}

	@Test
	void mapsRedisFailureToServiceUnavailable() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.increment(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
		RateLimiter limiter = new RateLimiter(redis, 5);

		assertThatThrownBy(() -> limiter.checkCreate("client-a")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(503);
	}
}

package com.example.shortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SuppressWarnings("null")
class LinkCacheTest {

	@Test
	void cacheTtlNeverOutlivesLinkExpiry() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		Instant now = Instant.parse("2026-08-27T12:00:00Z");
		RedisCircuitBreaker circuitBreaker = new RedisCircuitBreaker(Clock.fixed(now, ZoneOffset.UTC), 3,
				Duration.ofSeconds(5));
		LinkCache cache = new LinkCache(redis, mapper, Duration.ofHours(24), Duration.ofSeconds(30), circuitBreaker);

		cache.put("soon", new CachedLink("https://example.com", now.plusSeconds(300), null), now);

		ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
		verify(values).set(eq("link:soon"), org.mockito.ArgumentMatchers.anyString(), ttl.capture());
		assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void bypassesRedisAfterConsecutiveFailures() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
		Instant now = Instant.parse("2026-08-27T12:00:00Z");
		RedisCircuitBreaker circuitBreaker = new RedisCircuitBreaker(Clock.fixed(now, ZoneOffset.UTC), 2,
				Duration.ofSeconds(5));
		LinkCache cache = new LinkCache(redis, new ObjectMapper(), Duration.ofHours(24), Duration.ofSeconds(30),
				circuitBreaker);

		assertThat(cache.get("code").status()).isEqualTo(CacheLookup.Status.UNAVAILABLE);
		assertThat(cache.get("code").status()).isEqualTo(CacheLookup.Status.UNAVAILABLE);
		assertThat(cache.get("code").status()).isEqualTo(CacheLookup.Status.UNAVAILABLE);

		verify(redis, times(2)).opsForValue();
	}
}
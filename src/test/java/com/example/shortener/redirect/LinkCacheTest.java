package com.example.shortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LinkCacheTest {

	@Test
	void cacheTtlNeverOutlivesLinkExpiry() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		LinkCache cache = new LinkCache(redis, mapper, Duration.ofHours(24), Duration.ofSeconds(30));
		Instant now = Instant.parse("2026-08-27T12:00:00Z");

		cache.put("soon", new CachedLink("https://example.com", now.plusSeconds(300), null), now);

		ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
		verify(values).set(eq("link:soon"), org.mockito.ArgumentMatchers.anyString(), ttl.capture());
		assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(5));
	}
}
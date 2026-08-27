package com.example.shortener.redirect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LinkCache {

	private static final String NEGATIVE = "__missing__";
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Duration defaultTtl;
	private final Duration negativeTtl;

	public LinkCache(StringRedisTemplate redis, ObjectMapper objectMapper,
			@Value("${app.cache.default-ttl}") Duration defaultTtl,
			@Value("${app.cache.negative-ttl}") Duration negativeTtl) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.defaultTtl = defaultTtl;
		this.negativeTtl = negativeTtl;
	}

	public CacheLookup get(String shortCode) {
		try {
			String value = redis.opsForValue().get(key(shortCode));
			if (value == null) {
				return CacheLookup.status(CacheLookup.Status.MISS);
			}
			if (NEGATIVE.equals(value)) {
				return CacheLookup.status(CacheLookup.Status.NEGATIVE);
			}
			return CacheLookup.hit(objectMapper.readValue(value, CachedLink.class));
		} catch (RuntimeException | JsonProcessingException exception) {
			return CacheLookup.status(CacheLookup.Status.UNAVAILABLE);
		}
	}

	public void put(String shortCode, CachedLink link, Instant now) {
		Duration ttl = defaultTtl;
		if (link.expiresAt() != null) {
			ttl = ttl.compareTo(Duration.between(now, link.expiresAt())) > 0
					? Duration.between(now, link.expiresAt())
					: ttl;
		}
		if (ttl.isNegative() || ttl.isZero()) {
			return;
		}
		try {
			redis.opsForValue().set(key(shortCode), objectMapper.writeValueAsString(link), ttl);
		} catch (RuntimeException | JsonProcessingException ignored) {
			// Redis is an optimization; database resolution remains authoritative.
		}
	}

	public void putMissing(String shortCode) {
		try {
			redis.opsForValue().set(key(shortCode), NEGATIVE, negativeTtl);
		} catch (RuntimeException ignored) {
			// A failed negative-cache write must not alter lookup semantics.
		}
	}

	public void evict(String shortCode) {
		try {
			redis.delete(key(shortCode));
		} catch (RuntimeException ignored) {
			// Expiry and database lifecycle checks bound stale cache behavior.
		}
	}

	private String key(String shortCode) {
		return "link:" + shortCode;
	}
}
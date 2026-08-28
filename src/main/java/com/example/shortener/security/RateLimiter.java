package com.example.shortener.security;

import com.example.shortener.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

	private final StringRedisTemplate redis;
	private final long limit;

	public RateLimiter(StringRedisTemplate redis, @Value("${app.rate-limit.creates-per-minute}") long limit) {
		this.redis = redis;
		this.limit = limit;
	}

	public void checkCreate(String clientId) {
		long window = Instant.now().getEpochSecond() / 60;
		String key = "rate:create:" + clientId + ":" + window;
		try {
			Long count = redis.opsForValue().increment(key);
			if (count != null && count == 1) {
				redis.expire(key, Duration.ofMinutes(2));
			}
			if (count != null && count > limit) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded",
						"Create rate limit exceeded");
			}
		} catch (ApiException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "rate-limiter-unavailable",
					"Create requests are temporarily unavailable");
		}
	}
}
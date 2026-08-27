package com.example.shortener.security;

import com.example.shortener.common.ApiException;
import com.example.shortener.management.OwnerKeyHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyAuthenticator {

	private final List<String> validKeys;
	private final OwnerKeyHasher hasher;

	public ApiKeyAuthenticator(@Value("${app.security.api-keys}") String configuredKeys, OwnerKeyHasher hasher) {
		this.validKeys = Arrays.stream(configuredKeys.split(",")).map(String::trim).filter(value -> !value.isEmpty())
				.toList();
		this.hasher = hasher;
	}

	public String authenticate(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
			throw unauthorized();
		}
		String candidate = authorization.substring(7);
		boolean valid = validKeys.stream().anyMatch(key -> MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
				candidate.getBytes(StandardCharsets.UTF_8)));
		if (!valid) {
			throw unauthorized();
		}
		return hasher.hash(candidate);
	}

	private ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "invalid-api-key", "A valid Bearer API key is required");
	}
}
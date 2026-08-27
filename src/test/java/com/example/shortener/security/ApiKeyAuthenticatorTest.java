package com.example.shortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.common.ApiException;
import com.example.shortener.management.OwnerKeyHasher;
import org.junit.jupiter.api.Test;

class ApiKeyAuthenticatorTest {

	private final ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator("first, second", new OwnerKeyHasher());

	@Test
	void authenticatesConfiguredBearerKey() {
		assertThat(authenticator.authenticate("Bearer second")).hasSize(64);
	}

	@Test
	void rejectsMissingAndUnknownKeys() {
		assertThatThrownBy(() -> authenticator.authenticate(null)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> authenticator.authenticate("Bearer unknown")).isInstanceOf(ApiException.class);
	}
}
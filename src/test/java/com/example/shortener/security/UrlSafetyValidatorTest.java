package com.example.shortener.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.common.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyValidatorTest {

	private final UrlSafetyValidator validator = new UrlSafetyValidator();

	@ParameterizedTest
	@ValueSource(strings = {"javascript:alert(1)", "data:text/plain,hello", "file:///etc/passwd",
			"http://localhost/test", "http://127.0.0.1/test", "http://169.254.169.254/latest/meta-data",
			"http://service.internal/path", "https://user:password@example.com"})
	void rejectsUnsafeTargets(String target) {
		assertThatThrownBy(() -> validator.validate(target)).isInstanceOf(ApiException.class);
	}
}
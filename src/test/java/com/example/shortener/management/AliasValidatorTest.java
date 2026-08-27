package com.example.shortener.management;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.common.ApiException;
import org.junit.jupiter.api.Test;

class AliasValidatorTest {

	private final AliasValidator validator = new AliasValidator();

	@Test
	void acceptsValidAlias() {
		assertThatCode(() -> validator.validate("q3-campaign")).doesNotThrowAnyException();
	}

	@Test
	void rejectsInvalidAndReservedAliases() {
		assertThatThrownBy(() -> validator.validate("ab")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> validator.validate("api")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> validator.validate("malware-link")).isInstanceOf(ApiException.class);
	}
}
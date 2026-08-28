package com.example.shortener.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.shortener.common.ApiException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class UrlSafetyValidatorTest {

	private final UrlSafetyValidator validator = new UrlSafetyValidator();

	@ParameterizedTest
	@ValueSource(strings = {"javascript:alert(1)", "data:text/plain,hello", "file:///etc/passwd",
			"http://localhost/test", "http://127.0.0.1/test", "http://169.254.169.254/latest/meta-data",
			"http://service.internal/path", "https://user:password@example.com"})
	void rejectsUnsafeTargets(String target) {
		assertThatThrownBy(() -> validator.validate(target)).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	@Test
	void acceptsPublicHttpAndHttpsTargets() throws Exception {
		UrlSafetyValidator publicValidator = validatorResolvingTo("93.184.216.34");

		assertThatCode(() -> publicValidator.validate("https://example.com/docs")).doesNotThrowAnyException();
		assertThatCode(() -> publicValidator.validate("http://example.com/docs")).doesNotThrowAnyException();
	}

	@Test
	void rejectsWhenAnyResolvedAddressIsPrivate() throws Exception {
		UrlSafetyValidator mixedValidator = new UrlSafetyValidator(
				host -> new InetAddress[]{InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1")});

		assertThatThrownBy(() -> mixedValidator.validate("https://example.com")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	@Test
	void acceptsPublicIpv6Target() throws Exception {
		UrlSafetyValidator ipv6Validator = validatorResolvingTo("2001:4860:4860::8888");

		assertThatCode(() -> ipv6Validator.validate("https://[2001:4860:4860::8888]/")).doesNotThrowAnyException();
	}

	@Test
	void rejectsUnresolvedHost() {
		UrlSafetyValidator unresolvedValidator = new UrlSafetyValidator(host -> {
			throw new UnknownHostException(host);
		});

		assertThatThrownBy(() -> unresolvedValidator.validate("https://missing.example"))
				.isInstanceOf(ApiException.class).extracting(exception -> ((ApiException) exception).getStatus())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	private UrlSafetyValidator validatorResolvingTo(String address) throws Exception {
		InetAddress resolved = InetAddress.getByName(address);
		return new UrlSafetyValidator(host -> new InetAddress[]{resolved});
	}
}
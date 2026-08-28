package com.example.shortener.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsNotFoundToTheRfc7807Envelope() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing-code");
		ApiException exception = new ApiException(HttpStatus.NOT_FOUND, "link-not-found", "Link was not found");

		ProblemDetail detail = handler.handleApiException(exception, request);

		assertThat(detail.getStatus()).isEqualTo(404);
		assertThat(detail.getTitle()).isEqualTo("Not Found");
		assertThat(detail.getDetail()).isEqualTo("Link was not found");
		assertThat(detail.getType()).isEqualTo(URI.create("https://sho.rt/errors/link-not-found"));
		assertThat(detail.getInstance()).isEqualTo(URI.create("/missing-code"));
	}

	@Test
	void mapsServiceUnavailableToTheRfc7807Envelope() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some-code");
		ApiException exception = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "link-store-unavailable",
				"Link resolution is temporarily unavailable");

		ProblemDetail detail = handler.handleApiException(exception, request);

		assertThat(detail.getStatus()).isEqualTo(503);
		assertThat(detail.getTitle()).isEqualTo("Service Unavailable");
		assertThat(detail.getDetail()).isEqualTo("Link resolution is temporarily unavailable");
		assertThat(detail.getType()).isEqualTo(URI.create("https://sho.rt/errors/link-store-unavailable"));
		assertThat(detail.getInstance()).isEqualTo(URI.create("/some-code"));
	}
}

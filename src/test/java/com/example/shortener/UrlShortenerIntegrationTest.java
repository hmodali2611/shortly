package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UrlShortenerIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16-alpine"));

	@Container
	@SuppressWarnings("resource")
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("app.security.api-keys", () -> "integration-key");
		registry.add("app.analytics.ip-hash-secret", () -> "integration-secret");
	}

	@LocalServerPort
	private int port;
	@Autowired
	private TestRestTemplate rest;

	@Test
	void createRedirectReadMetadataAndDelete() {
		HttpHeaders headers = authenticatedHeaders();
		headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
		Map<String, Object> request = Map.of("targetUrl", "https://example.com/docs", "customAlias", "integration-link",
				"expiresAt", Instant.now().plusSeconds(600).toString());

		ResponseEntity<JsonNode> created = rest.exchange(url("/api/v1/links"), HttpMethod.POST,
				new HttpEntity<>(request, headers), JsonNode.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody().path("shortCode").asText()).isEqualTo("integration-link");

		ResponseEntity<JsonNode> metadata = rest.exchange(url("/api/v1/links/integration-link"), HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertThat(metadata.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(metadata.getBody().path("targetUrl").asText()).isEqualTo("https://example.com/docs");

		ResponseEntity<Void> redirect = rest.getForEntity(url("/integration-link"), Void.class);
		assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/docs");

		ResponseEntity<Void> deleted = rest.exchange(url("/api/v1/links/integration-link"), HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<JsonNode> gone = rest.getForEntity(url("/integration-link"), JsonNode.class);
		assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.GONE);
	}

	private HttpHeaders authenticatedHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("integration-key");
		return headers;
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
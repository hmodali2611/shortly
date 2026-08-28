package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
		registry.add("app.analytics.ip-hash-secret", () -> "integration-secret");
	}

	@LocalServerPort
	private int port;
	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createRedirectReadMetadataAndDelete() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
		Map<String, Object> request = Map.of("targetUrl", "https://example.com/docs", "customAlias", "integration-link",
				"expiresAt", Instant.now().plusSeconds(600).toString());

		ResponseEntity<JsonNode> created = rest.exchange(url("/api/v1/links"), HttpMethod.POST,
				new HttpEntity<>(request, headers), JsonNode.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(Objects.requireNonNull(created.getBody()).path("shortCode").asText()).isEqualTo("integration-link");

		ResponseEntity<JsonNode> metadata = rest.exchange(url("/api/v1/links/integration-link"), HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertThat(metadata.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(Objects.requireNonNull(metadata.getBody()).path("targetUrl").asText())
				.isEqualTo("https://example.com/docs");

		HttpResponse<Void> redirect = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build().send(
				HttpRequest.newBuilder(URI.create(url("/integration-link"))).GET().build(),
				HttpResponse.BodyHandlers.discarding());
		assertThat(redirect.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(redirect.headers().firstValue(HttpHeaders.LOCATION)).contains("https://example.com/docs");
		jdbcTemplate.update(
				"INSERT INTO click_events (short_code, occurred_at, referrer, user_agent, ip_hash) VALUES (?, ?, ?, ?, ?)",
				"integration-link", Timestamp.from(Instant.now()), "https://example.com", "integration-test",
				"a".repeat(64));

		ResponseEntity<Void> deleted = rest.exchange(url("/api/v1/links/integration-link"), HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<JsonNode> gone = rest.getForEntity(url("/integration-link"), JsonNode.class);
		assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.GONE);

		ResponseEntity<JsonNode> stats = rest.getForEntity(url("/api/v1/links/integration-link/stats"), JsonNode.class);
		assertThat(stats.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(Objects.requireNonNull(stats.getBody()).path("totalClicks").asLong()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void servesSwaggerUiAndOpenApiDocument() throws Exception {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
		HttpResponse<Void> entrypoint = client.send(
				HttpRequest.newBuilder(URI.create(url("/swagger-ui.html"))).GET().build(),
				HttpResponse.BodyHandlers.discarding());

		assertThat(entrypoint.statusCode()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(entrypoint.headers().firstValue(HttpHeaders.LOCATION)).contains("/swagger-ui/index.html");
		assertThat(rest.getForEntity(url("/swagger-ui/index.html"), String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(rest.getForEntity(url("/v3/api-docs"), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
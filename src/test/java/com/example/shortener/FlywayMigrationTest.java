package com.example.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16-alpine"));

	@Test
	void upgradesPopulatedV2SchemaToV3WithoutLosingData() throws Exception {
		flyway().target(MigrationVersion.fromVersion("2")).load().migrate();
		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO links
					(short_code, target_url, created_at, expires_at, owner_key_id, is_custom_alias)
					VALUES ('upgrade-link', 'https://example.com', now(), now() + interval '1 day', 'owner', false)
					""");
			statement.executeUpdate("""
					INSERT INTO click_events (short_code, occurred_at, ip_hash)
					VALUES ('upgrade-link', now(), repeat('a', 64))
					""");
		}

		flyway().load().migrate();

		try (Connection connection = connection(); Statement statement = connection.createStatement()) {
			assertThat(singleString(statement, "SELECT target_url FROM links WHERE short_code = 'upgrade-link'"))
					.isEqualTo("https://example.com");
			assertThat(singleLong(statement, "SELECT count(*) FROM click_events WHERE short_code = 'upgrade-link'"))
					.isOne();
			assertThat(singleLong(statement, """
					SELECT count(*) FROM information_schema.columns
					WHERE table_schema = 'public' AND table_name = 'links' AND column_name = 'owner_key_id'
					""")).isZero();
			assertThat(singleLong(statement, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_links_owner'"))
					.isZero();
		}
	}

	private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
		return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private Connection connection() throws Exception {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private String singleString(Statement statement, String query) throws Exception {
		try (ResultSet result = statement.executeQuery(query)) {
			assertThat(result.next()).isTrue();
			return result.getString(1);
		}
	}

	private long singleLong(Statement statement, String query) throws Exception {
		try (ResultSet result = statement.executeQuery(query)) {
			assertThat(result.next()).isTrue();
			return result.getLong(1);
		}
	}
}
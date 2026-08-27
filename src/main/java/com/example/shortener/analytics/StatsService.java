package com.example.shortener.analytics;

import com.example.shortener.management.LinkService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

	private final JdbcTemplate jdbcTemplate;
	private final LinkService linkService;
	private final Clock clock;

	public StatsService(JdbcTemplate jdbcTemplate, LinkService linkService, Clock clock) {
		this.jdbcTemplate = jdbcTemplate;
		this.linkService = linkService;
		this.clock = clock;
	}

	public AnalyticsResponse get(String shortCode, String ownerKeyId) {
		linkService.metadata(shortCode, ownerKeyId);
		Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM click_events WHERE short_code = ?", Long.class,
				shortCode);
		Long unique = jdbcTemplate.queryForObject(
				"SELECT count(DISTINCT ip_hash) FROM click_events WHERE short_code = ?", Long.class, shortCode);
		Long recent = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM click_events WHERE short_code = ? AND occurred_at >= ?", Long.class, shortCode,
				Timestamp.from(clock.instant().minus(24, ChronoUnit.HOURS)));
		ClickBounds bounds = jdbcTemplate.queryForObject(
				"SELECT min(occurred_at), max(occurred_at) FROM click_events WHERE short_code = ?",
				(resultSet, row) -> new ClickBounds(toInstant(resultSet.getTimestamp(1)),
						toInstant(resultSet.getTimestamp(2))),
				shortCode);
		return new AnalyticsResponse(shortCode, value(total), value(unique), value(recent),
				bounds == null ? null : bounds.first(), bounds == null ? null : bounds.last(),
				breakdown(shortCode, "referrer"), breakdown(shortCode, "user_agent"), clock.instant());
	}

	private List<AnalyticsResponse.Breakdown> breakdown(String shortCode, String column) {
		String sql = "SELECT coalesce(" + column + ", 'unknown'), count(*) AS total FROM click_events "
				+ "WHERE short_code = ? GROUP BY " + column + " ORDER BY total DESC LIMIT 10";
		return jdbcTemplate.query(sql,
				(resultSet, row) -> new AnalyticsResponse.Breakdown(resultSet.getString(1), resultSet.getLong(2)),
				shortCode);
	}

	private Instant toInstant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private long value(Long value) {
		return value == null ? 0 : value;
	}

	private record ClickBounds(Instant first, Instant last) {
	}
}
package com.example.shortener.analytics;

import com.example.shortener.management.LinkService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
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

	public AnalyticsResponse get(String shortCode) {
		linkService.metadata(shortCode);
		AnalyticsSummary summary = Objects.requireNonNull(jdbcTemplate.queryForObject(
				"SELECT count(*), count(DISTINCT ip_hash), count(*) FILTER (WHERE occurred_at >= ?), "
						+ "min(occurred_at), max(occurred_at) FROM click_events WHERE short_code = ?",
				(resultSet, row) -> new AnalyticsSummary(resultSet.getLong(1), resultSet.getLong(2),
						resultSet.getLong(3), toInstant(resultSet.getTimestamp(4)),
						toInstant(resultSet.getTimestamp(5))),
				Timestamp.from(clock.instant().minus(24, ChronoUnit.HOURS)), shortCode));
		return new AnalyticsResponse(shortCode, summary.total(), summary.unique(), summary.recent(), summary.first(),
				summary.last(), breakdown(shortCode, "referrer"), breakdown(shortCode, "user_agent"), clock.instant());
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

	private record AnalyticsSummary(long total, long unique, long recent, Instant first, Instant last) {
	}
}
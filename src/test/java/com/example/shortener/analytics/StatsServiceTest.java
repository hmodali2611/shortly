package com.example.shortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shortener.management.LinkService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("null")
class StatsServiceTest {

	@Test
	@SuppressWarnings("unchecked")
	void returnsAggregateAnalyticsWithFreshnessTimestamp() throws Exception {
		Instant now = Instant.parse("2026-08-27T12:00:00Z");
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		LinkService links = mock(LinkService.class);
		when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<Object> mapper = invocation.getArgument(1);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getLong(1)).thenReturn(12L);
			when(resultSet.getLong(2)).thenReturn(7L);
			when(resultSet.getLong(3)).thenReturn(3L);
			when(resultSet.getTimestamp(4)).thenReturn(Timestamp.from(now.minusSeconds(60)));
			when(resultSet.getTimestamp(5)).thenReturn(Timestamp.from(now));
			return mapper.mapRow(resultSet, 0);
		});
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
				.thenReturn(List.of(new AnalyticsResponse.Breakdown("example.com", 8L)))
				.thenReturn(List.of(new AnalyticsResponse.Breakdown("browser", 9L)));
		StatsService service = new StatsService(jdbc, links, Clock.fixed(now, ZoneOffset.UTC));

		AnalyticsResponse response = service.get("code");

		verify(links).metadata("code");
		verify(jdbc, times(1)).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
		assertThat(response.totalClicks()).isEqualTo(12);
		assertThat(response.uniqueVisitors()).isEqualTo(7);
		assertThat(response.clicksLast24h()).isEqualTo(3);
		assertThat(response.firstClickAt()).isEqualTo(now.minusSeconds(60));
		assertThat(response.lastClickAt()).isEqualTo(now);
		assertThat(response.topReferrers()).extracting(AnalyticsResponse.Breakdown::value)
				.containsExactly("example.com");
		assertThat(response.devices()).extracting(AnalyticsResponse.Breakdown::value).containsExactly("browser");
		assertThat(response.asOf()).isEqualTo(now);
	}
}
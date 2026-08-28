package com.example.shortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

@SuppressWarnings("null")
class ClickFlusherTest {

	@Test
	void drainsMultipleBatchesInOneFlush() {
		ClickRecorder recorder = new ClickRecorder(501);
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		ClickEvent event = new ClickEvent("code", Instant.EPOCH, null, null, "hash");
		for (int index = 0; index < 501; index++) {
			recorder.record(event);
		}

		new ClickFlusher(recorder, jdbcTemplate).flush();

		verify(jdbcTemplate, times(2)).batchUpdate(anyString(), ArgumentMatchers.<ClickEvent>anyList(), eq(500),
				ArgumentMatchers.<ParameterizedPreparedStatementSetter<ClickEvent>>any());
		assertThat(recorder.drainTo(new ArrayList<>(), 1)).isZero();
	}

	@Test
	void restoresBatchWhenDatabaseWriteFails() {
		ClickRecorder recorder = new ClickRecorder(1);
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		recorder.record(new ClickEvent("code", Instant.EPOCH, null, null, "hash"));
		when(jdbcTemplate.batchUpdate(anyString(), ArgumentMatchers.<ClickEvent>anyList(), eq(500),
				ArgumentMatchers.<ParameterizedPreparedStatementSetter<ClickEvent>>any()))
				.thenThrow(new IllegalStateException("offline"));

		new ClickFlusher(recorder, jdbcTemplate).flush();

		assertThat(recorder.drainTo(new ArrayList<>(), 1)).isOne();
		assertThat(recorder.droppedCount()).isZero();
	}
}
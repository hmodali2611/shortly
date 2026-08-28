package com.example.shortener.analytics;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClickFlusher {

	private static final int BATCH_SIZE = 500;
	private static final int MAX_BATCHES_PER_FLUSH = 20;
	private final ClickRecorder recorder;
	private final JdbcTemplate jdbcTemplate;

	public ClickFlusher(ClickRecorder recorder, JdbcTemplate jdbcTemplate) {
		this.recorder = recorder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Scheduled(fixedDelayString = "${app.analytics.flush-delay:2000}")
	public void flush() {
		List<ClickEvent> batch = new ArrayList<>(BATCH_SIZE);
		for (int batchNumber = 0; batchNumber < MAX_BATCHES_PER_FLUSH; batchNumber++) {
			batch.clear();
			recorder.drainTo(batch, BATCH_SIZE);
			if (batch.isEmpty()) {
				return;
			}
			try {
				jdbcTemplate.batchUpdate(
						"INSERT INTO click_events (short_code, occurred_at, referrer, user_agent, ip_hash) VALUES (?, ?, ?, ?, ?)",
						batch, BATCH_SIZE, (statement, event) -> {
							statement.setString(1, event.shortCode());
							statement.setTimestamp(2, Timestamp.from(event.occurredAt()));
							statement.setString(3, event.referrer());
							statement.setString(4, event.userAgent());
							statement.setString(5, event.ipHash());
						});
			} catch (RuntimeException exception) {
				batch.forEach(recorder::restore);
				return;
			}
		}
	}
}
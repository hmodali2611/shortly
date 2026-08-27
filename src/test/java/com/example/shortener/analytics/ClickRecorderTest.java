package com.example.shortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClickRecorderTest {

	@Test
	void dropsEventsInsteadOfGrowingBeyondCapacity() {
		ClickRecorder recorder = new ClickRecorder(1);
		ClickEvent event = new ClickEvent("code", Instant.EPOCH, null, null, "hash");

		recorder.record(event);
		recorder.record(event);

		List<ClickEvent> drained = new ArrayList<>();
		assertThat(recorder.drainTo(drained, 10)).isOne();
		assertThat(recorder.droppedCount()).isOne();
	}
}
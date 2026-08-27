package com.example.shortener.analytics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClickRecorder {

	private final BlockingQueue<ClickEvent> queue;
	private final AtomicLong dropped = new AtomicLong();

	public ClickRecorder(@Value("${app.analytics.queue-capacity}") int capacity) {
		this.queue = new ArrayBlockingQueue<>(capacity);
	}

	public void record(ClickEvent event) {
		if (!queue.offer(event)) {
			dropped.incrementAndGet();
		}
	}

	int drainTo(java.util.Collection<ClickEvent> events, int maximum) {
		return queue.drainTo(events, maximum);
	}

	void restore(ClickEvent event) {
		if (!queue.offer(event)) {
			dropped.incrementAndGet();
		}
	}

	public long droppedCount() {
		return dropped.get();
	}
}
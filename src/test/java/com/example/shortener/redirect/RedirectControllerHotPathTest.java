package com.example.shortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.shortener.analytics.ClickEvent;
import com.example.shortener.analytics.ClickFlusher;
import com.example.shortener.analytics.ClickRecorder;
import com.example.shortener.analytics.IpHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Covers two non-functional properties of the redirect hot path that were
 * previously documented as "planned" rather than executed: NFR-4/T-11 (click
 * tracking must never block a redirect, even if the batch sink is stalled) and
 * NFR-6 (no raw client address or target URL in application logs).
 */
class RedirectControllerHotPathTest {

	@Test
	void redirectStaysFastWhileClickFlushIsStalledOnTheDatabase() throws Exception {
		ClickRecorder recorder = new ClickRecorder(4);
		LinkResolver resolver = mock(LinkResolver.class);
		when(resolver.resolve(anyString())).thenReturn("https://example.com/target");
		IpHasher ipHasher = mock(IpHasher.class);
		when(ipHasher.hash(anyString())).thenReturn("hash");
		Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
		RedirectController controller = new RedirectController(resolver, recorder, ipHasher, clock);

		// One event for the flusher to pick up and stall on.
		controller.redirect("seed", newRequest());

		JdbcTemplate stalledJdbc = mock(JdbcTemplate.class);
		CountDownLatch flushStarted = new CountDownLatch(1);
		CountDownLatch releaseFlush = new CountDownLatch(1);
		when(stalledJdbc.batchUpdate(anyString(), ArgumentMatchers.<ClickEvent>anyList(), eq(500),
				ArgumentMatchers.<ParameterizedPreparedStatementSetter<ClickEvent>>any())).thenAnswer(invocation -> {
					flushStarted.countDown();
					releaseFlush.await(10, TimeUnit.SECONDS);
					return new int[0][0];
				});

		Thread flushThread = new Thread(() -> new ClickFlusher(recorder, stalledJdbc).flush());
		flushThread.setDaemon(true);
		flushThread.start();
		assertThat(flushStarted.await(5, TimeUnit.SECONDS)).as("flusher reached the stalled database write").isTrue();

		AtomicLong elapsedMillis = new AtomicLong(-1);
		Thread hotPathThread = new Thread(() -> {
			long start = System.nanoTime();
			for (int i = 0; i < 50; i++) {
				ResponseEntity<Void> response = controller.redirect("hot-path", newRequest());
				if (response.getStatusCode().value() != 302) {
					throw new AssertionError("expected 302, got " + response.getStatusCode());
				}
			}
			elapsedMillis.set((System.nanoTime() - start) / 1_000_000);
		});
		hotPathThread.setDaemon(true);
		hotPathThread.start();
		hotPathThread.join(2_000);

		releaseFlush.countDown();
		flushThread.join(5_000);

		assertThat(hotPathThread.isAlive())
				.as("50 redirects must return while the click flusher is blocked on a stalled database write")
				.isFalse();
		assertThat(elapsedMillis.get())
				.as("50 redirects took %dms while the flush thread was stalled on the database", elapsedMillis.get())
				.isBetween(0L, 2_000L);
		// Capacity 4: the first 4 offers fit, the remaining 46 of 50 must be dropped,
		// not blocked.
		assertThat(recorder.droppedCount())
				.as("a full queue with a stalled sink must drop events instead of blocking the caller").isEqualTo(46);
	}

	@Test
	void neverLogsRawClientAddressOrTargetUrl() {
		String secretMarker = "secret-" + UUID.randomUUID();
		String targetUrl = "https://internal.example.com/private?token=" + secretMarker;
		String remoteAddress = "203.0.113.55";

		ClickRecorder recorder = new ClickRecorder(8);
		LinkResolver resolver = mock(LinkResolver.class);
		when(resolver.resolve("redaction-check")).thenReturn(targetUrl);
		IpHasher ipHasher = mock(IpHasher.class);
		when(ipHasher.hash(remoteAddress)).thenReturn("irrelevant-hash");
		Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
		RedirectController controller = new RedirectController(resolver, recorder, ipHasher, clock);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/redaction-check");
		request.setRemoteAddr(remoteAddress);
		request.addHeader("Referer", "https://referrer.example.com/" + secretMarker);

		Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		rootLogger.addAppender(appender);
		ResponseEntity<Void> response;
		try {
			response = controller.redirect("redaction-check", request);
		} finally {
			rootLogger.detachAppender(appender);
		}

		assertThat(response.getStatusCode().value()).isEqualTo(302);

		List<String> logged = appender.list.stream()
				.flatMap(event -> Stream.of(event.getFormattedMessage(), String.valueOf(event.getMDCPropertyMap())))
				.filter(Objects::nonNull).toList();

		assertThat(logged).as("the target URL must never appear verbatim in application logs")
				.noneMatch(message -> message.contains(secretMarker));
		assertThat(logged).as("the raw client address must never appear verbatim in application logs")
				.noneMatch(message -> message.contains(remoteAddress));
	}

	private MockHttpServletRequest newRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hot-path");
		request.setRemoteAddr("127.0.0.1");
		return request;
	}
}

package com.example.shortener.redirect;

import com.example.shortener.analytics.ClickEvent;
import com.example.shortener.analytics.ClickRecorder;
import com.example.shortener.analytics.IpHasher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

	private final LinkResolver resolver;
	private final ClickRecorder recorder;
	private final IpHasher ipHasher;
	private final Clock clock;

	public RedirectController(LinkResolver resolver, ClickRecorder recorder, IpHasher ipHasher, Clock clock) {
		this.resolver = resolver;
		this.recorder = recorder;
		this.ipHasher = ipHasher;
		this.clock = clock;
	}

	@GetMapping("/{shortCode:[a-zA-Z0-9_-]{3,32}}")
	ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
		String target = resolver.resolve(shortCode);
		recorder.record(new ClickEvent(shortCode, clock.instant(), truncate(request.getHeader("Referer"), 512),
				truncate(request.getHeader("User-Agent"), 512), ipHasher.hash(request.getRemoteAddr())));
		return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(target).toASCIIString())
				.build();
	}

	private String truncate(String value, int maximum) {
		return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
	}
}
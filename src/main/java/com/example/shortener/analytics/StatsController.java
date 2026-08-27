package com.example.shortener.analytics;

import com.example.shortener.security.ApiKeyAuthenticator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class StatsController {

	private final StatsService statsService;
	private final ApiKeyAuthenticator authenticator;

	public StatsController(StatsService statsService, ApiKeyAuthenticator authenticator) {
		this.statsService = statsService;
		this.authenticator = authenticator;
	}

	@GetMapping("/{shortCode}/stats")
	AnalyticsResponse stats(@PathVariable String shortCode,
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		return statsService.get(shortCode, authenticator.authenticate(authorization));
	}
}
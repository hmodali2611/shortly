package com.example.shortener.analytics;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(String shortCode, long totalClicks, long uniqueVisitors, long clicksLast24h,
		Instant firstClickAt, Instant lastClickAt, List<Breakdown> topReferrers, List<Breakdown> devices,
		Instant asOf) {

	public record Breakdown(String value, long count) {
	}
}
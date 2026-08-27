package com.example.shortener.analytics;

import java.time.Instant;

public record ClickEvent(String shortCode, Instant occurredAt, String referrer, String userAgent, String ipHash) {
}
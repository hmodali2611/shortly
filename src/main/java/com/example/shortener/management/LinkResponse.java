package com.example.shortener.management;

import com.example.shortener.common.LinkEntity;
import java.time.Instant;

public record LinkResponse(String shortCode, String shortUrl, String targetUrl, Instant createdAt, Instant expiresAt,
		Instant deletedAt) {

	static LinkResponse from(LinkEntity link, String baseUrl) {
		return new LinkResponse(link.getShortCode(), baseUrl + "/" + link.getShortCode(), link.getTargetUrl(),
				link.getCreatedAt(), link.getExpiresAt(), link.getDeletedAt());
	}
}
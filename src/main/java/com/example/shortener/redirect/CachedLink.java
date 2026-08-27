package com.example.shortener.redirect;

import com.example.shortener.common.LinkEntity;
import java.time.Instant;

public record CachedLink(String targetUrl, Instant expiresAt, Instant deletedAt) {

	static CachedLink from(LinkEntity link) {
		return new CachedLink(link.getTargetUrl(), link.getExpiresAt(), link.getDeletedAt());
	}
}
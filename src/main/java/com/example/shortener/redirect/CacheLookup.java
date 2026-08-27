package com.example.shortener.redirect;

public record CacheLookup(Status status, CachedLink link) {

	public enum Status {
		HIT, MISS, NEGATIVE, UNAVAILABLE
	}

	static CacheLookup hit(CachedLink link) {
		return new CacheLookup(Status.HIT, link);
	}

	static CacheLookup status(Status status) {
		return new CacheLookup(status, null);
	}
}
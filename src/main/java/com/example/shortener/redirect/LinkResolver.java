package com.example.shortener.redirect;

import com.example.shortener.common.ApiException;
import com.example.shortener.common.LinkEntity;
import com.example.shortener.common.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LinkResolver {

	private final LinkCache cache;
	private final LinkRepository repository;
	private final Clock clock;

	public LinkResolver(LinkCache cache, LinkRepository repository, Clock clock) {
		this.cache = cache;
		this.repository = repository;
		this.clock = clock;
	}

	public String resolve(String shortCode) {
		CacheLookup lookup = cache.get(shortCode);
		if (lookup.status() == CacheLookup.Status.NEGATIVE) {
			throw notFound();
		}
		if (lookup.status() == CacheLookup.Status.HIT) {
			return activeTarget(lookup.link());
		}
		try {
			LinkEntity link = repository.findById(shortCode).orElse(null);
			if (link == null) {
				cache.putMissing(shortCode);
				throw notFound();
			}
			CachedLink cached = CachedLink.from(link);
			cache.put(shortCode, cached, clock.instant());
			return activeTarget(cached);
		} catch (ApiException exception) {
			throw exception;
		} catch (DataAccessException exception) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "link-store-unavailable",
					"Link resolution is temporarily unavailable");
		}
	}

	private String activeTarget(CachedLink link) {
		Instant now = clock.instant();
		if (link.deletedAt() != null || (link.expiresAt() != null && !link.expiresAt().isAfter(now))) {
			throw new ApiException(HttpStatus.GONE, "link-gone", "Link has expired or been deleted");
		}
		return link.targetUrl();
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "link-not-found", "Link was not found");
	}
}
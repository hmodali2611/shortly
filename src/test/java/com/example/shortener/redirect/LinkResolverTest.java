package com.example.shortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shortener.common.ApiException;
import com.example.shortener.common.LinkEntity;
import com.example.shortener.common.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class LinkResolverTest {

	private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
	@Mock
	private LinkCache cache;
	@Mock
	private LinkRepository repository;

	@Test
	void rejectsExpiredWarmCacheEntry() {
		when(cache.get("expired"))
				.thenReturn(CacheLookup.hit(new CachedLink("https://example.com", NOW.minusSeconds(1), null)));
		LinkResolver resolver = new LinkResolver(cache, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> resolver.resolve("expired")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(410);
	}

	@Test
	void fallsBackToDatabaseWhenCacheUnavailable() {
		LinkEntity link = new LinkEntity("active", "https://example.com", NOW, NOW.plusSeconds(60), "owner", false);
		when(cache.get("active")).thenReturn(CacheLookup.status(CacheLookup.Status.UNAVAILABLE));
		when(repository.findById("active")).thenReturn(Optional.of(link));
		LinkResolver resolver = new LinkResolver(cache, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(resolver.resolve("active")).isEqualTo("https://example.com");
		verify(cache).put("active", CachedLink.from(link), NOW);
	}

	@Test
	void negativeCacheReturnsNotFoundWithoutDatabaseLookup() {
		when(cache.get("missing")).thenReturn(CacheLookup.status(CacheLookup.Status.NEGATIVE));
		LinkResolver resolver = new LinkResolver(cache, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> resolver.resolve("missing")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(404);
	}

	@Test
	void unavailableDatabaseReturnsServiceUnavailable() {
		when(cache.get("unknown")).thenReturn(CacheLookup.status(CacheLookup.Status.MISS));
		when(repository.findById("unknown")).thenThrow(new DataAccessResourceFailureException("offline"));
		LinkResolver resolver = new LinkResolver(cache, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> resolver.resolve("unknown")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(503);
	}
}
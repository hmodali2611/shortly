package com.example.shortener.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shortener.common.ApiException;
import com.example.shortener.common.LinkEntity;
import com.example.shortener.common.LinkRepository;
import com.example.shortener.redirect.LinkCache;
import com.example.shortener.security.UrlSafetyValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SuppressWarnings("null")
class LinkServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
	private LinkRepository repository;
	private ShortCodeGenerator generator;
	private LinkCache cache;
	private LinkService service;

	@BeforeEach
	void setUp() {
		repository = mock(LinkRepository.class);
		generator = mock(ShortCodeGenerator.class);
		cache = mock(LinkCache.class);
		UrlSafetyValidator safety = mock(UrlSafetyValidator.class);
		doNothing().when(safety).validate(any());
		service = new LinkService(repository, generator, new AliasValidator(), Clock.fixed(NOW, ZoneOffset.UTC), cache,
				safety, Duration.ofDays(365), "https://sho.rt");
	}

	@Test
	void retriesGeneratedCodeCollision() {
		when(generator.generate()).thenReturn("collision", "available");
		when(repository.saveAndFlush(any(LinkEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"))
				.thenAnswer(invocation -> invocation.getArgument(0));

		LinkResponse response = service.create(new CreateLinkRequest("https://example.com", null, null));

		assertThat(response.shortCode()).isEqualTo("available");
	}

	@Test
	void customAliasCollisionReturnsConflictWithoutRetry() {
		when(repository.saveAndFlush(any(LinkEntity.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", "campaign", null)))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(409);
	}

	@Test
	void deleteEvictsCachedLink() {
		LinkEntity link = new LinkEntity("campaign", "https://example.com", NOW, NOW.plusSeconds(60), true);
		when(repository.findById("campaign")).thenReturn(Optional.of(link));

		service.delete("campaign");

		assertThat(link.getDeletedAt()).isEqualTo(NOW);
		verify(cache).evict("campaign");
	}

	@Test
	void deleteEvictsCachedLinkOnlyAfterTransactionCommit() {
		LinkEntity link = new LinkEntity("campaign", "https://example.com", NOW, NOW.plusSeconds(60), true);
		when(repository.findById("campaign")).thenReturn(Optional.of(link));
		TransactionSynchronizationManager.initSynchronization();
		try {
			service.delete("campaign");

			verify(cache, never()).evict("campaign");
			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
			verify(cache).evict("campaign");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void deleteUnknownLinkReturnsNotFound() {
		when(repository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(404);
		verifyNoInteractions(cache);
	}

	@Test
	void deletingAlreadyDeletedLinkIsIdempotent() {
		LinkEntity link = new LinkEntity("campaign", "https://example.com", NOW, NOW.plusSeconds(60), true);
		Instant deletedAt = NOW.minusSeconds(30);
		link.delete(deletedAt);
		when(repository.findById("campaign")).thenReturn(Optional.of(link));

		service.delete("campaign");

		assertThat(link.getDeletedAt()).isEqualTo(deletedAt);
		verifyNoInteractions(cache);
	}

	@Test
	void rejectsPastExpiryBeforeWriting() {
		assertThatThrownBy(
				() -> service.create(new CreateLinkRequest("https://example.com", null, NOW.minusSeconds(1))))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(400);
		verifyNoInteractions(repository);
	}

	@Test
	void returnsMetadataWithoutOwnerAuthentication() {
		LinkEntity link = new LinkEntity("private", "https://example.com", NOW, NOW.plusSeconds(60), false);
		when(repository.findById("private")).thenReturn(Optional.of(link));

		assertThat(service.metadata("private").shortCode()).isEqualTo("private");
	}

	@Test
	void returnsServiceUnavailableAfterFiveGeneratedCollisions() {
		when(generator.generate()).thenReturn("collision");
		when(repository.saveAndFlush(any(LinkEntity.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null, null)))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getStatus().value()).isEqualTo(503);
		verify(generator, times(5)).generate();
		verify(repository, times(5)).saveAndFlush(any(LinkEntity.class));
	}
}
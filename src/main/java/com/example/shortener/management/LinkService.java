package com.example.shortener.management;

import com.example.shortener.common.ApiException;
import com.example.shortener.common.LinkEntity;
import com.example.shortener.common.LinkRepository;
import com.example.shortener.redirect.LinkCache;
import com.example.shortener.security.UrlSafetyValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LinkService {

	private static final int MAX_GENERATION_ATTEMPTS = 5;
	private final LinkRepository repository;
	private final ShortCodeGenerator generator;
	private final AliasValidator aliasValidator;
	private final Clock clock;
	private final LinkCache linkCache;
	private final UrlSafetyValidator urlSafetyValidator;
	private final Duration defaultTtl;
	private final String baseUrl;

	public LinkService(LinkRepository repository, ShortCodeGenerator generator, AliasValidator aliasValidator,
			Clock clock, LinkCache linkCache, UrlSafetyValidator urlSafetyValidator,
			@Value("${app.links.default-ttl}") Duration defaultTtl, @Value("${app.base-url}") String baseUrl) {
		this.repository = repository;
		this.generator = generator;
		this.aliasValidator = aliasValidator;
		this.clock = clock;
		this.linkCache = linkCache;
		this.urlSafetyValidator = urlSafetyValidator;
		this.defaultTtl = defaultTtl;
		this.baseUrl = baseUrl;
	}

	public LinkResponse create(CreateLinkRequest request) {
		urlSafetyValidator.validate(request.targetUrl());
		Instant now = clock.instant();
		Instant expiresAt = request.expiresAt() == null ? now.plus(defaultTtl) : request.expiresAt();
		if (!expiresAt.isAfter(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-expiry", "Expiry must be in the future");
		}
		if (request.customAlias() != null && !request.customAlias().isBlank()) {
			aliasValidator.validate(request.customAlias());
			return insert(request.customAlias(), request.targetUrl(), now, expiresAt, true, false);
		}
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			LinkResponse response = insert(generator.generate(), request.targetUrl(), now, expiresAt, false, true);
			if (response != null) {
				return response;
			}
		}
		throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "code-generation-exhausted",
				"Unable to allocate a short code");
	}

	@Transactional(readOnly = true)
	public LinkResponse metadata(String shortCode) {
		return LinkResponse.from(requireLink(shortCode), baseUrl);
	}

	@Transactional
	public void delete(String shortCode) {
		LinkEntity link = requireLink(shortCode);
		if (link.getDeletedAt() == null) {
			link.delete(clock.instant());
			evictAfterCommit(shortCode);
		}
	}

	private void evictAfterCommit(String shortCode) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			linkCache.evict(shortCode);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				linkCache.evict(shortCode);
			}
		});
	}

	private LinkResponse insert(String shortCode, String targetUrl, Instant createdAt, Instant expiresAt,
			boolean customAlias, boolean retryCollision) {
		try {
			LinkEntity link = repository
					.saveAndFlush(new LinkEntity(shortCode, targetUrl, createdAt, expiresAt, customAlias));
			return LinkResponse.from(link, baseUrl);
		} catch (DataIntegrityViolationException exception) {
			if (retryCollision) {
				return null;
			}
			throw new ApiException(HttpStatus.CONFLICT, "alias-taken", "The requested alias is already in use");
		}
	}

	private LinkEntity requireLink(String shortCode) {
		return repository.findById(shortCode)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "link-not-found", "Link was not found"));
	}
}
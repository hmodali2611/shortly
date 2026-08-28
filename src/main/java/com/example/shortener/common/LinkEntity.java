package com.example.shortener.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "links")
public class LinkEntity {

	@Id
	@Column(name = "short_code", length = 32, nullable = false)
	private String shortCode;

	@Column(name = "target_url", length = 2048, nullable = false)
	private String targetUrl;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "is_custom_alias", nullable = false)
	private boolean customAlias;

	protected LinkEntity() {
	}

	public LinkEntity(String shortCode, String targetUrl, Instant createdAt, Instant expiresAt, boolean customAlias) {
		this.shortCode = shortCode;
		this.targetUrl = targetUrl;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.customAlias = customAlias;
	}

	public String getShortCode() {
		return shortCode;
	}

	public String getTargetUrl() {
		return targetUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public boolean isCustomAlias() {
		return customAlias;
	}

	public void delete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
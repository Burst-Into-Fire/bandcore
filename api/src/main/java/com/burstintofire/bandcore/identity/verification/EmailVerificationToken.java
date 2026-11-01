package com.burstintofire.bandcore.identity.verification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/** Global table: the SHA-256 hash of a single-use email verification token. */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends AssignedIdEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private byte[] tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EmailVerificationToken() {
	}

	public EmailVerificationToken(UUID id, UUID userId, byte[] tokenHash, Instant expiresAt, Instant now) {
		super(id);
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	public UUID getUserId() {
		return userId;
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

}

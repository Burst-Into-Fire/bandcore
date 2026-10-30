package com.burstintofire.bandcore.identity.session;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Global table: one refresh token of a session. All tokens of a login share a family; using a
 * token rotates it, and using an already rotated token revokes the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AssignedIdEntity {

	@Column(name = "family_id", nullable = false, updatable = false)
	private UUID familyId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** The band of the session, or null for a central session. */
	@Column(name = "band_id", updatable = false)
	private UUID bandId;

	/** The host the session belongs to; the cookie is host-only, and this is checked as well. */
	@Column(nullable = false, updatable = false)
	private String audience;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private byte[] tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "family_expires_at", nullable = false, updatable = false)
	private Instant familyExpiresAt;

	@Column(name = "rotated_at")
	private Instant rotatedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected RefreshToken() {
	}

	RefreshToken(UUID id, UUID familyId, UUID userId, UUID bandId, String audience, byte[] tokenHash, Instant now,
			Instant expiresAt, Instant familyExpiresAt) {
		super(id);
		this.familyId = familyId;
		this.userId = userId;
		this.bandId = bandId;
		this.audience = audience;
		this.tokenHash = tokenHash;
		this.createdAt = now;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getAudience() {
		return audience;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getFamilyExpiresAt() {
		return familyExpiresAt;
	}

	boolean isRotated() {
		return rotatedAt != null;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt) || !now.isBefore(familyExpiresAt);
	}

	void markRotated(Instant now) {
		this.rotatedAt = now;
	}

}

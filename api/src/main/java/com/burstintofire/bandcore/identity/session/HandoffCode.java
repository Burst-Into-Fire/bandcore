package com.burstintofire.bandcore.identity.session;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/** Global table: a one-time code that moves a session to another host. */
@Entity
@Table(name = "handoff_codes")
class HandoffCode extends AssignedIdEntity {

	@Column(name = "code_hash", nullable = false, updatable = false)
	private byte[] codeHash;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "target_band", updatable = false)
	private UUID targetBand;

	@Column(name = "target_host", nullable = false, updatable = false)
	private String targetHost;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected HandoffCode() {
	}

	HandoffCode(UUID id, byte[] codeHash, UUID userId, UUID targetBand, String targetHost, Instant expiresAt,
			Instant now) {
		super(id);
		this.codeHash = codeHash;
		this.userId = userId;
		this.targetBand = targetBand;
		this.targetHost = targetHost;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getTargetBand() {
		return targetBand;
	}

	/** Unused, unexpired, and for exactly this host. */
	boolean isUsableOn(String host, Instant now) {
		return usedAt == null && now.isBefore(expiresAt) && targetHost.equals(host);
	}

	void markUsed(Instant now) {
		this.usedAt = now;
	}

}

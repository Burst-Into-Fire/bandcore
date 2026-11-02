package com.burstintofire.bandcore.tenancy.invitation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;
import com.burstintofire.bandcore.tenancy.Role;

/** Tenant table: an invitation to join the band with a role. The token lives in invitation_tokens. */
@Entity
@Table(name = "invitations")
public class Invitation extends AssignedIdEntity {

	/** What the API reports, with "expired" derived from the expiry. */
	public enum State {

		PENDING, ACCEPTED, EXPIRED, REVOKED

	}

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(nullable = false, updatable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(name = "member_id")
	private UUID memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "invited_by", nullable = false)
	private UUID invitedBy;

	@Column(name = "accepted_by")
	private UUID acceptedBy;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Invitation() {
	}

	Invitation(UUID id, UUID bandId, String email, Role role, UUID memberId, UUID invitedBy, Instant expiresAt,
			Instant now) {
		super(id);
		this.bandId = bandId;
		this.email = email;
		this.role = role;
		this.memberId = memberId;
		this.status = InvitationStatus.PENDING;
		this.invitedBy = invitedBy;
		this.expiresAt = expiresAt;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public UUID getMemberId() {
		return memberId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public UUID getInvitedBy() {
		return invitedBy;
	}

	public State state(Instant now) {
		return switch (status) {
			case ACCEPTED -> State.ACCEPTED;
			case REVOKED -> State.REVOKED;
			case PENDING -> now.isBefore(expiresAt) ? State.PENDING : State.EXPIRED;
		};
	}

	boolean isPending() {
		return status == InvitationStatus.PENDING;
	}

	void renew(Instant newExpiry, Instant now) {
		this.expiresAt = newExpiry;
		this.updatedAt = now;
	}

	void revoke(Instant now) {
		this.status = InvitationStatus.REVOKED;
		this.revokedAt = now;
		this.updatedAt = now;
	}

	void accept(UUID userId, Instant now) {
		this.status = InvitationStatus.ACCEPTED;
		this.acceptedBy = userId;
		this.acceptedAt = now;
		this.updatedAt = now;
	}

}

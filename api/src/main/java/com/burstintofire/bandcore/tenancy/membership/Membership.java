package com.burstintofire.bandcore.tenancy.membership;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;
import com.burstintofire.bandcore.tenancy.Role;

/** Tenant table: a user's access to a band, with a role. */
@Entity
@Table(name = "memberships")
public class Membership extends AssignedIdEntity {

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MembershipStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Membership() {
	}

	public Membership(UUID id, UUID bandId, UUID userId, Role role, Instant now) {
		super(id);
		this.bandId = bandId;
		this.userId = userId;
		this.role = role;
		this.status = MembershipStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public UUID getUserId() {
		return userId;
	}

	public Role getRole() {
		return role;
	}

	public MembershipStatus getStatus() {
		return status;
	}

	public boolean isActive() {
		return status == MembershipStatus.ACTIVE;
	}

	void changeRole(Role newRole, Instant now) {
		this.role = newRole;
		this.updatedAt = now;
	}

	void remove(Instant now) {
		this.status = MembershipStatus.REMOVED;
		this.updatedAt = now;
	}

}

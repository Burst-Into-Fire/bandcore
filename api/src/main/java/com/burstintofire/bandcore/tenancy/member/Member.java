package com.burstintofire.bandcore.tenancy.member;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/** Tenant table: a band member (integrante), with or without a login. */
@Entity
@Table(name = "members")
public class Member extends AssignedIdEntity {

	public static final int MAX_DISPLAY_NAME_LENGTH = 80;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column
	private String instrument;

	@Column(name = "user_id")
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MemberStatus status;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Member() {
	}

	public Member(UUID id, UUID bandId, String displayName, UUID userId, Instant now) {
		super(id);
		this.bandId = bandId;
		this.displayName = displayName;
		this.userId = userId;
		this.status = MemberStatus.ACTIVE;
		this.joinedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getInstrument() {
		return instrument;
	}

	public UUID getUserId() {
		return userId;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public Instant getLeftAt() {
		return leftAt;
	}

}

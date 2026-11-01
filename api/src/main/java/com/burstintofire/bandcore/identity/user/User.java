package com.burstintofire.bandcore.identity.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * A global account: one email, one password, any number of bands (ADR-0003). Global table,
 * read and written only by the identity module.
 */
@Entity
@Table(name = "users")
public class User extends AssignedIdEntity {

	public static final int MAX_DISPLAY_NAME_LENGTH = 80;

	@Column(nullable = false, updatable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(UUID id, String email, String passwordHash, String displayName, Instant now) {
		super(id);
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.status = UserStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	public UserStatus getStatus() {
		return status;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public void markEmailVerified(Instant now) {
		if (emailVerifiedAt == null) {
			this.emailVerifiedAt = now;
			this.updatedAt = now;
		}
	}

	public void changePasswordHash(String newHash, Instant now) {
		this.passwordHash = newHash;
		this.updatedAt = now;
	}

}

package com.burstintofire.bandcore.tenancy.band;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * The tenant. A global table (no {@code band_id}, no RLS): it is the tenant itself, so it is
 * only read and written by the tenancy module.
 */
@Entity
@Table(name = "bands")
public class Band extends AssignedIdEntity {

	public static final String DEFAULT_CURRENCY = "BRL";

	public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

	@Column(nullable = false, updatable = false)
	private String slug;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String currency;

	@Column(nullable = false)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BandStatus status;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Band() {
	}

	Band(UUID id, String slug, String name, UUID createdBy, Instant now) {
		super(id);
		this.slug = slug;
		this.name = name;
		this.createdBy = createdBy;
		this.currency = DEFAULT_CURRENCY;
		this.timezone = DEFAULT_TIMEZONE;
		this.status = BandStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public String getSlug() {
		return slug;
	}

	public String getName() {
		return name;
	}

	public String getCurrency() {
		return currency;
	}

	public String getTimezone() {
		return timezone;
	}

	public BandStatus getStatus() {
		return status;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public boolean isActive() {
		return status == BandStatus.ACTIVE;
	}

	void rename(String newName, Instant now) {
		this.name = newName;
		this.updatedAt = now;
	}

	void changeTimezone(String newTimezone, Instant now) {
		this.timezone = newTimezone;
		this.updatedAt = now;
	}

	void changeStatus(BandStatus newStatus, Instant now) {
		this.status = newStatus;
		this.updatedAt = now;
	}

}

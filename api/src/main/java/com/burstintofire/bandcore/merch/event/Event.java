package com.burstintofire.bandcore.merch.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/** Tenant table: a show at which the band can register sales. */
@Entity
@Table(name = "events")
public class Event extends AssignedIdEntity {

	public static final int MAX_NAME_LENGTH = 120;

	public static final int MAX_VENUE_LENGTH = 120;

	public static final int MAX_CITY_LENGTH = 80;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(nullable = false)
	private String name;

	@Column
	private String venue;

	@Column
	private String city;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventStatus status;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "closed_by")
	private UUID closedBy;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Event() {
	}

	Event(UUID id, UUID bandId, UUID createdBy, Instant now) {
		super(id);
		this.bandId = bandId;
		this.createdBy = createdBy;
		this.status = EventStatus.OPEN;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getName() {
		return name;
	}

	public String getVenue() {
		return venue;
	}

	public String getCity() {
		return city;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public EventStatus getStatus() {
		return status;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public UUID getClosedBy() {
		return closedBy;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	void change(String newName, String newVenue, String newCity, Instant newStartsAt) {
		this.name = newName;
		this.venue = newVenue;
		this.city = newCity;
		this.startsAt = newStartsAt;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	Map<String, Object> snapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", name);
		snapshot.put("venue", venue);
		snapshot.put("city", city);
		snapshot.put("starts_at", startsAt.toString());
		snapshot.put("status", status.name());
		return snapshot;
	}

}

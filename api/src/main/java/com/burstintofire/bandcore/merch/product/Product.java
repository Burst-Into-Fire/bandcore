package com.burstintofire.bandcore.merch.product;

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

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Tenant table: a merch item such as "Camiseta preta". Its prices and its cost are in one
 * currency (ADR-0009). A product with history is archived, never deleted.
 */
@Entity
@Table(name = "products")
public class Product extends AssignedIdEntity {

	public static final int MAX_NAME_LENGTH = 120;

	public static final int MAX_DESCRIPTION_LENGTH = 500;

	/** R$ 10.000.000,00: far above any merch price, and keeps price × quantity well inside a long. */
	public static final long MAX_PRICE_CENTS = 1_000_000_000L;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Currency currency;

	@Column(name = "default_price_cents", nullable = false)
	private long defaultPriceCents;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Product() {
	}

	Product(UUID id, UUID bandId, UUID createdBy, Instant now) {
		super(id);
		this.bandId = bandId;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Currency getCurrency() {
		return currency;
	}

	public long getDefaultPriceCents() {
		return defaultPriceCents;
	}

	public boolean isArchived() {
		return archivedAt != null;
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

	void change(String newName, String newDescription, Currency newCurrency, long priceCents) {
		this.name = newName;
		this.description = newDescription;
		this.currency = newCurrency;
		this.defaultPriceCents = priceCents;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	void archive(Instant now) {
		this.archivedAt = now;
		this.updatedAt = now;
	}

	void unarchive(Instant now) {
		this.archivedAt = null;
		this.updatedAt = now;
	}

	/** The audited fields. The description is free text: only its length. */
	Map<String, Object> snapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", name);
		snapshot.put("description_length", description == null ? 0 : description.length());
		snapshot.put("currency", currency == null ? null : currency.name());
		snapshot.put("default_price_cents", defaultPriceCents);
		return snapshot;
	}

}

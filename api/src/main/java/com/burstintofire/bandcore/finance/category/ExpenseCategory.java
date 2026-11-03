package com.burstintofire.bandcore.finance.category;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Tenant table: a category that organizes expenses. A category in use is archived, never
 * deleted: archived categories stay valid on existing expenses but are not offered for new ones.
 */
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory extends AssignedIdEntity {

	public static final int MAX_NAME_LENGTH = 40;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(nullable = false)
	private String name;

	/** Set while a default category keeps its original name; cleared when it is renamed. */
	@Enumerated(EnumType.STRING)
	@Column(name = "default_key")
	private DefaultCategory defaultKey;

	/** {@code #rrggbb} in lowercase, or null. */
	@Column
	private String color;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ExpenseCategory() {
	}

	ExpenseCategory(UUID id, UUID bandId, String name, String color, Instant now) {
		super(id);
		this.bandId = bandId;
		this.name = name;
		this.color = color;
		this.createdAt = now;
		this.updatedAt = now;
	}

	static ExpenseCategory ofDefault(UUID id, UUID bandId, DefaultCategory key, Instant now) {
		ExpenseCategory category = new ExpenseCategory(id, bandId, key.defaultName(), null, now);
		category.defaultKey = key;
		return category;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getName() {
		return name;
	}

	public DefaultCategory getDefaultKey() {
		return defaultKey;
	}

	public String getColor() {
		return color;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	void rename(String newName, Instant now) {
		this.name = newName;
		this.defaultKey = null;
		this.updatedAt = now;
	}

	void changeColor(String newColor, Instant now) {
		this.color = newColor;
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

}

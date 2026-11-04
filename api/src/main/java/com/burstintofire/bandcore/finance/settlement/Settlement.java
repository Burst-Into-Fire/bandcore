package com.burstintofire.bandcore.finance.settlement;

import java.time.Instant;
import java.time.LocalDate;
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

/** Tenant table: one member paid another, in one currency, to settle balances (RF18). */
@Entity
@Table(name = "settlements")
public class Settlement extends AssignedIdEntity {

	public static final int MAX_NOTE_LENGTH = 500;

	public static final long MAX_AMOUNT_CENTS = 1_000_000_000_000L;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "from_member_id", nullable = false)
	private UUID fromMemberId;

	@Column(name = "to_member_id", nullable = false)
	private UUID toMemberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Currency currency;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "paid_on", nullable = false)
	private LocalDate paidOn;

	@Column
	private String note;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Settlement() {
	}

	Settlement(UUID id, UUID bandId, UUID createdBy, Instant now) {
		super(id);
		this.bandId = bandId;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public UUID getFromMemberId() {
		return fromMemberId;
	}

	public UUID getToMemberId() {
		return toMemberId;
	}

	public Currency getCurrency() {
		return currency;
	}

	public long getAmountCents() {
		return amountCents;
	}

	public LocalDate getPaidOn() {
		return paidOn;
	}

	public String getNote() {
		return note;
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

	void change(UUID from, UUID to, Currency newCurrency, long cents, LocalDate date, String newNote) {
		this.fromMemberId = from;
		this.toMemberId = to;
		this.currency = newCurrency;
		this.amountCents = cents;
		this.paidOn = date;
		this.note = newNote;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	void softDelete(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}

	/** The audited fields. The note is free text: only its length. */
	Map<String, Object> snapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("from_member_id", fromMemberId == null ? null : fromMemberId.toString());
		snapshot.put("to_member_id", toMemberId == null ? null : toMemberId.toString());
		snapshot.put("currency", currency == null ? null : currency.name());
		snapshot.put("amount_cents", amountCents);
		snapshot.put("paid_on", paidOn == null ? null : paidOn.toString());
		snapshot.put("note_length", note == null ? 0 : note.length());
		return snapshot;
	}

}

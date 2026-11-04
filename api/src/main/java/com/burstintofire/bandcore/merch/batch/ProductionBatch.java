package com.burstintofire.bandcore.merch.batch;

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

/**
 * Tenant table: merch received from a supplier at a cost (RF24). Quantities and costs never
 * change after it is registered; supplier, order date and notes can be edited.
 */
@Entity
@Table(name = "production_batches")
public class ProductionBatch extends AssignedIdEntity {

	public static final int MAX_SUPPLIER_LENGTH = 120;

	public static final int MAX_NOTES_LENGTH = 500;

	/** Also the largest expense, so a batch cost can always become one. */
	public static final long MAX_TOTAL_COST_CENTS = 1_000_000_000_000L;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column
	private String supplier;

	@Column(name = "ordered_on")
	private LocalDate orderedOn;

	@Column(name = "received_on", nullable = false, updatable = false)
	private LocalDate receivedOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Currency currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "cost_mode", nullable = false, updatable = false)
	private CostMode costMode;

	@Column(name = "total_cost_cents", nullable = false, updatable = false)
	private long totalCostCents;

	@Column
	private String notes;

	/** Registering the batch also created an expense for its cost (M7-04). */
	@Column(name = "expense_requested", nullable = false, updatable = false)
	private boolean expenseRequested;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BatchStatus status;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancelled_by")
	private UUID cancelledBy;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductionBatch() {
	}

	ProductionBatch(UUID id, UUID bandId, LocalDate receivedOn, Currency currency, CostMode costMode,
			long totalCostCents, boolean expenseRequested, UUID createdBy, Instant now) {
		super(id);
		this.bandId = bandId;
		this.receivedOn = receivedOn;
		this.currency = currency;
		this.costMode = costMode;
		this.totalCostCents = totalCostCents;
		this.expenseRequested = expenseRequested;
		this.status = BatchStatus.RECEIVED;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public String getSupplier() {
		return supplier;
	}

	public LocalDate getOrderedOn() {
		return orderedOn;
	}

	public LocalDate getReceivedOn() {
		return receivedOn;
	}

	public Currency getCurrency() {
		return currency;
	}

	public CostMode getCostMode() {
		return costMode;
	}

	public long getTotalCostCents() {
		return totalCostCents;
	}

	public String getNotes() {
		return notes;
	}

	public boolean isExpenseRequested() {
		return expenseRequested;
	}

	public BatchStatus getStatus() {
		return status;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
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

	void describe(String newSupplier, LocalDate newOrderedOn, String newNotes) {
		this.supplier = newSupplier;
		this.orderedOn = newOrderedOn;
		this.notes = newNotes;
	}

	void touch(Instant now) {
		this.updatedAt = now;
	}

	void cancel(UUID userId, Instant now) {
		this.status = BatchStatus.CANCELLED;
		this.cancelledBy = userId;
		this.cancelledAt = now;
		this.updatedAt = now;
	}

	/** The editable fields, audited. Supplier and notes are free text: only their length. */
	Map<String, Object> details() {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("supplier_length", supplier == null ? 0 : supplier.length());
		details.put("ordered_on", orderedOn == null ? null : orderedOn.toString());
		details.put("notes_length", notes == null ? 0 : notes.length());
		return details;
	}

}

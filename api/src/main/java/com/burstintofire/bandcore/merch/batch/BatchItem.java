package com.burstintofire.bandcore.merch.batch;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Tenant table, immutable: units of one variant that a production batch brought into stock, what
 * they cost, and the variant's stock right before them, so the batch can be cancelled exactly.
 */
@Entity
@Table(name = "batch_items")
public class BatchItem extends AssignedIdEntity {

	public static final int MAX_QUANTITY = 100_000;

	/** R$ 10.000.000,00 per unit; with {@link #MAX_QUANTITY}, a line stays far inside a long. */
	public static final long MAX_UNIT_COST_CENTS = 1_000_000_000L;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "batch_id", nullable = false, updatable = false)
	private UUID batchId;

	@Column(name = "variant_id", nullable = false, updatable = false)
	private UUID variantId;

	@Column(nullable = false, updatable = false)
	private int position;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(name = "line_cost_cents", nullable = false, updatable = false)
	private long lineCostCents;

	@Column(name = "quantity_before", nullable = false, updatable = false)
	private int quantityBefore;

	@Column(name = "average_cost_before_cents", updatable = false)
	private Long averageCostBeforeCents;

	@Column(name = "movement_id", nullable = false, updatable = false)
	private UUID movementId;

	protected BatchItem() {
	}

	BatchItem(UUID id, UUID bandId, UUID batchId, UUID variantId, int position, int quantity, long lineCostCents,
			int quantityBefore, Long averageCostBeforeCents, UUID movementId) {
		super(id);
		this.bandId = bandId;
		this.batchId = batchId;
		this.variantId = variantId;
		this.position = position;
		this.quantity = quantity;
		this.lineCostCents = lineCostCents;
		this.quantityBefore = quantityBefore;
		this.averageCostBeforeCents = averageCostBeforeCents;
		this.movementId = movementId;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public UUID getVariantId() {
		return variantId;
	}

	public int getPosition() {
		return position;
	}

	public int getQuantity() {
		return quantity;
	}

	public long getLineCostCents() {
		return lineCostCents;
	}

	public int getQuantityBefore() {
		return quantityBefore;
	}

	public Long getAverageCostBeforeCents() {
		return averageCostBeforeCents;
	}

	public UUID getMovementId() {
		return movementId;
	}

}

package com.burstintofire.bandcore.merch.sale;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/** Tenant table: immutable quantity, price, cost, and optional discount snapshots for one variant. */
@Entity
@Table(name = "sale_items")
public class SaleItem extends AssignedIdEntity {

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "sale_id", nullable = false, updatable = false)
	private UUID saleId;

	@Column(name = "variant_id", nullable = false, updatable = false)
	private UUID variantId;

	@Column(nullable = false, updatable = false)
	private int position;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(name = "unit_price_cents", nullable = false, updatable = false)
	private long unitPriceCents;

	@Column(name = "unit_cost_cents", updatable = false)
	private Long unitCostCents;

	@Column(name = "discount_cents", nullable = false, updatable = false)
	private long discountCents;

	@Column(name = "discount_reason", updatable = false)
	private String discountReason;

	protected SaleItem() {
	}

	SaleItem(UUID id, UUID bandId, UUID saleId, UUID variantId, int position, int quantity, long unitPriceCents,
			Long unitCostCents, long discountCents, String discountReason) {
		super(id);
		this.bandId = bandId;
		this.saleId = saleId;
		this.variantId = variantId;
		this.position = position;
		this.quantity = quantity;
		this.unitPriceCents = unitPriceCents;
		this.unitCostCents = unitCostCents;
		this.discountCents = discountCents;
		this.discountReason = discountReason;
	}

	public UUID getBandId() {
		return bandId;
	}

	public UUID getSaleId() {
		return saleId;
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

	public long getUnitPriceCents() {
		return unitPriceCents;
	}

	public Long getUnitCostCents() {
		return unitCostCents;
	}

	public long getDiscountCents() {
		return discountCents;
	}

	public String getDiscountReason() {
		return discountReason;
	}

}

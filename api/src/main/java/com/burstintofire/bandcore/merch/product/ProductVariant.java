package com.burstintofire.bandcore.merch.product;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/**
 * Tenant table: one sellable version of a product, for example size M of "Camiseta preta".
 * Size and color are optional: a product without variations has one variant with neither.
 * The price overrides the product's when set.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariant extends AssignedIdEntity {

	public static final int MAX_SIZE_LENGTH = 20;

	public static final int MAX_COLOR_LENGTH = 40;

	public static final int MAX_SKU_LENGTH = 40;

	public static final int MAX_LOW_STOCK_THRESHOLD = 100_000;

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column
	private String size;

	@Column
	private String color;

	@Column
	private String sku;

	@Column(name = "price_cents")
	private Long priceCents;

	@Column(name = "low_stock_threshold", nullable = false)
	private int lowStockThreshold;

	/** Display order within the product, as the admin entered the variants. */
	@Column(nullable = false)
	private int position;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductVariant() {
	}

	ProductVariant(UUID id, UUID bandId, UUID productId, int position, Instant now) {
		super(id);
		this.bandId = bandId;
		this.productId = productId;
		this.position = position;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getBandId() {
		return bandId;
	}

	public UUID getProductId() {
		return productId;
	}

	public String getSize() {
		return size;
	}

	public String getColor() {
		return color;
	}

	public String getSku() {
		return sku;
	}

	/** The variant's own price, or null when it sells at the product's price. */
	public Long getPriceCents() {
		return priceCents;
	}

	public int getLowStockThreshold() {
		return lowStockThreshold;
	}

	public int getPosition() {
		return position;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	public long getVersion() {
		return version;
	}

	/** The price a sale uses: the variant's own, or the product's. */
	public long effectivePriceCents(Product product) {
		return priceCents != null ? priceCents : product.getDefaultPriceCents();
	}

	void change(String newSize, String newColor, String newSku, Long newPriceCents, int threshold) {
		this.size = newSize;
		this.color = newColor;
		this.sku = newSku;
		this.priceCents = newPriceCents;
		this.lowStockThreshold = threshold;
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

	/** The audited fields. */
	Map<String, Object> snapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("product_id", productId.toString());
		snapshot.put("size", size);
		snapshot.put("color", color);
		snapshot.put("sku", sku);
		snapshot.put("price_cents", priceCents);
		snapshot.put("low_stock_threshold", lowStockThreshold);
		return snapshot;
	}

}

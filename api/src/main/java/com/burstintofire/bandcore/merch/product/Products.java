package com.burstintofire.bandcore.merch.product;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.merch.stock.StockService;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * The merch catalog of the current band: products and their variants (M7-01). Everyone reads it;
 * admins change it. Products and variants with history are archived instead of deleted; archived
 * ones leave the active listing that sales screens use.
 */
@Service
public class Products {

	/** The values of a variant; blank texts mean "not set", a null price sells at the product's. */
	public record VariantInput(String size, String color, String sku, Long priceCents, int lowStockThreshold) {
	}

	/** A new product; without variants it gets one default variant (no size, no color). */
	public record NewProduct(String name, String description, Currency currency, long defaultPriceCents,
			List<VariantInput> variants) {
	}

	/** A partial update: null leaves a field unchanged; an empty description clears it. */
	public record ProductChanges(long version, String name, String description, Currency currency,
			Long defaultPriceCents) {
	}

	/** A product with its variants in display order, and their stock by variant id. */
	public record ProductDetails(Product product, List<ProductVariant> variants, Map<UUID, StockService.Level> stock) {

		public StockService.Level stockOf(ProductVariant variant) {
			return stock.get(variant.getId());
		}

	}

	/** A variant with the product it belongs to (for its effective price and currency) and its stock. */
	public record VariantDetails(Product product, ProductVariant variant, StockService.Level stock) {
	}

	/** Which products a listing includes. */
	public enum Listing {

		/** Products and variants that can be sold: archived ones are left out. */
		ACTIVE,

		/** The whole catalog, archived products and variants included. */
		ALL

	}

	static final String NAME_INDEX = "products_name_key";

	static final String ATTRIBUTES_INDEX = "product_variants_attributes_key";

	static final String SKU_INDEX = "product_variants_sku_key";

	private final ProductRepository products;

	private final ProductVariantRepository variants;

	private final StockService stock;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	Products(ProductRepository products, ProductVariantRepository variants, StockService stock, AuditRecorder audit,
			IdGenerator ids, Clock clock) {
		this.products = products;
		this.variants = variants;
		this.stock = stock;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public List<ProductDetails> list(Listing listing) {
		Map<UUID, List<ProductVariant>> byProduct = variants.findByBandId(bandId())
			.stream()
			.collect(Collectors.groupingBy(ProductVariant::getProductId, LinkedHashMap::new, Collectors.toList()));
		Map<UUID, StockService.Level> levels = stock
			.levels(byProduct.values().stream().flatMap(List::stream).map(ProductVariant::getId).toList());
		List<ProductDetails> details = new ArrayList<>();
		for (Product product : products.findByBandId(bandId())) {
			List<ProductVariant> own = byProduct.getOrDefault(product.getId(), List.of());
			if (listing == Listing.ACTIVE) {
				own = own.stream().filter(variant -> !variant.isArchived()).toList();
				if (product.isArchived() || own.isEmpty()) {
					continue;
				}
			}
			details.add(new ProductDetails(product, own, levels));
		}
		return details;
	}

	/** Another band's product answers 404, exactly like one that does not exist. */
	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public ProductDetails get(UUID productId) {
		return details(find(productId));
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public ProductDetails create(NewProduct input) {
		Instant now = clock.instant();
		Product product = new Product(ids.newId(), bandId(), CurrentUser.require().userId(), now);
		product.change(input.name().strip(), blankToNull(input.description()), input.currency(),
				input.defaultPriceCents());
		ensureNameFree(product.getName(), product.getId());
		List<VariantInput> requested = input.variants() == null || input.variants().isEmpty()
				? List.of(new VariantInput(null, null, null, null, 0)) : input.variants();
		ensureDistinct(requested);
		save(product);
		audit.record("product.created", "product", product.getId(), product.snapshot());
		List<ProductVariant> created = new ArrayList<>();
		for (int position = 0; position < requested.size(); position++) {
			created.add(addVariant(product, requested.get(position), position, "variants[" + position + "]", now));
		}
		return new ProductDetails(product, created,
				stock.levels(created.stream().map(ProductVariant::getId).toList()));
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public ProductDetails update(UUID productId, ProductChanges changes) {
		Product product = find(productId);
		requireVersion(product.getVersion(), changes.version());
		Map<String, Object> before = product.snapshot();
		if (changes.currency() != null && changes.currency() != product.getCurrency() && stock.anyMovements(
				variants.findByBandIdAndProductId(bandId(), product.getId()).stream().map(ProductVariant::getId).toList())) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_CURRENCY_LOCKED,
					"The product's stock has a cost in its currency, which can no longer change.", "currency");
		}
		String name = changes.name() != null ? changes.name().strip() : product.getName();
		if (!name.equals(product.getName())) {
			ensureNameFree(name, product.getId());
		}
		product.change(name,
				changes.description() != null ? blankToNull(changes.description()) : product.getDescription(),
				changes.currency() != null ? changes.currency() : product.getCurrency(),
				changes.defaultPriceCents() != null ? changes.defaultPriceCents() : product.getDefaultPriceCents());
		Map<String, Object> diff = AuditRecorder.diff(before, product.snapshot());
		if (!diff.isEmpty()) {
			product.touch(clock.instant());
			flush(product);
			audit.record("product.updated", "product", product.getId(), diff);
		}
		return details(product);
	}

	/** Archived products stay on past records but leave the active listing. */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public ProductDetails archive(UUID productId) {
		Product product = find(productId);
		if (!product.isArchived()) {
			product.archive(clock.instant());
			flush(product);
			audit.record("product.archived", "product", product.getId(), Map.of());
		}
		return details(product);
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public ProductDetails unarchive(UUID productId) {
		Product product = find(productId);
		if (product.isArchived()) {
			product.unarchive(clock.instant());
			flush(product);
			audit.record("product.unarchived", "product", product.getId(), Map.of());
		}
		return details(product);
	}

	/** Only a product whose variants never moved stock can be deleted; otherwise 409, archive it. */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public void delete(UUID productId, Long version) {
		Product product = find(productId);
		if (version != null) {
			requireVersion(product.getVersion(), version);
		}
		try {
			products.delete(product);
			products.flush();
		}
		catch (DataIntegrityViolationException referenced) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_HAS_HISTORY,
					"This product has stock history. Archive it instead.");
		}
		audit.record("product.deleted", "product", productId, Map.of("name", product.getName()));
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public VariantDetails addVariant(UUID productId, VariantInput input) {
		Product product = find(productId);
		ProductVariant variant = addVariant(product, input, variants.nextPosition(bandId(), product.getId()), null,
				clock.instant());
		return variantDetails(product, variant);
	}

	/** Replaces every value of a variant; send them all. */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public VariantDetails replaceVariant(UUID variantId, long version, VariantInput input) {
		ProductVariant variant = findVariant(variantId);
		requireVersion(variant.getVersion(), version);
		Map<String, Object> before = variant.snapshot();
		apply(variant, input, null);
		Map<String, Object> diff = AuditRecorder.diff(before, variant.snapshot());
		if (!diff.isEmpty()) {
			variant.touch(clock.instant());
			flush(variant);
			audit.record("product_variant.updated", "product_variant", variant.getId(), diff);
		}
		return variantDetails(productOf(variant), variant);
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public VariantDetails archiveVariant(UUID variantId) {
		ProductVariant variant = findVariant(variantId);
		if (!variant.isArchived()) {
			variant.archive(clock.instant());
			flush(variant);
			audit.record("product_variant.archived", "product_variant", variant.getId(), Map.of());
		}
		return variantDetails(productOf(variant), variant);
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public VariantDetails unarchiveVariant(UUID variantId) {
		ProductVariant variant = findVariant(variantId);
		if (variant.isArchived()) {
			variant.unarchive(clock.instant());
			flush(variant);
			audit.record("product_variant.unarchived", "product_variant", variant.getId(), Map.of());
		}
		return variantDetails(productOf(variant), variant);
	}

	/**
	 * Only a variant that never moved stock can be deleted (409 VARIANT_HAS_HISTORY), and a product
	 * keeps at least one variant (409 LAST_VARIANT).
	 */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public void deleteVariant(UUID variantId, Long version) {
		ProductVariant variant = findVariant(variantId);
		if (version != null) {
			requireVersion(variant.getVersion(), version);
		}
		if (variants.findByBandIdAndProductId(bandId(), variant.getProductId()).size() == 1) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.LAST_VARIANT,
					"A product keeps at least one variant. Delete the product instead.");
		}
		try {
			variants.delete(variant);
			variants.flush();
		}
		catch (DataIntegrityViolationException referenced) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VARIANT_HAS_HISTORY,
					"This variant has stock history. Archive it instead.");
		}
		audit.record("product_variant.deleted", "product_variant", variantId, variant.snapshot());
	}

	/**
	 * Variants of the current band by id, with their products and stock, for other parts of the
	 * merch module (batches, sales). Ids of other bands are left out; callers check permissions.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, VariantDetails> variants(Collection<UUID> variantIds) {
		if (variantIds.isEmpty()) {
			return Map.of();
		}
		List<ProductVariant> found = variants.findByBandIdAndIdIn(bandId(), List.copyOf(variantIds));
		Map<UUID, Product> owners = products
			.findByBandIdAndIdIn(bandId(), found.stream().map(ProductVariant::getProductId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Product::getId, product -> product));
		Map<UUID, StockService.Level> levels = stock.levels(variantIds);
		Map<UUID, VariantDetails> details = new LinkedHashMap<>();
		for (ProductVariant variant : found) {
			details.put(variant.getId(),
					new VariantDetails(owners.get(variant.getProductId()), variant, levels.get(variant.getId())));
		}
		return details;
	}

	private ProductVariant addVariant(Product product, VariantInput input, int position, String field, Instant now) {
		ProductVariant variant = new ProductVariant(ids.newId(), bandId(), product.getId(), position, now);
		apply(variant, input, field);
		save(variant);
		stock.open(variant.getId());
		audit.record("product_variant.created", "product_variant", variant.getId(), variant.snapshot());
		return variant;
	}

	/** Sets the values after checking that size, color and SKU are free in the band. */
	private void apply(ProductVariant variant, VariantInput input, String field) {
		String size = blankToNull(input.size());
		String color = blankToNull(input.color());
		String sku = blankToNull(input.sku());
		if (variants.attributesTaken(bandId(), variant.getProductId(), key(size), key(color), variant.getId())) {
			throw variantTaken(field);
		}
		if (sku != null && variants.skuTaken(bandId(), sku, variant.getId())) {
			throw skuTaken(field);
		}
		variant.change(size, color, sku, input.priceCents(), input.lowStockThreshold());
	}

	/** Variants of one new product must differ from each other in size and color, and in SKU. */
	private static void ensureDistinct(List<VariantInput> requested) {
		Set<String> attributes = new HashSet<>();
		Set<String> skus = new HashSet<>();
		for (int i = 0; i < requested.size(); i++) {
			VariantInput input = requested.get(i);
			if (!attributes.add(key(blankToNull(input.size())) + "\u0000" + key(blankToNull(input.color())))) {
				throw variantTaken("variants[" + i + "]");
			}
			String sku = blankToNull(input.sku());
			if (sku != null && !skus.add(key(sku))) {
				throw skuTaken("variants[" + i + "]");
			}
		}
	}

	private ProductDetails details(Product product) {
		List<ProductVariant> own = variants.findByBandIdAndProductId(bandId(), product.getId());
		return new ProductDetails(product, own, stock.levels(own.stream().map(ProductVariant::getId).toList()));
	}

	private VariantDetails variantDetails(Product product, ProductVariant variant) {
		return new VariantDetails(product, variant, stock.levels(List.of(variant.getId())).get(variant.getId()));
	}

	private Product find(UUID productId) {
		return products.findByBandIdAndId(bandId(), productId).orElseThrow(ApiException::notFound);
	}

	private ProductVariant findVariant(UUID variantId) {
		return variants.findByBandIdAndId(bandId(), variantId).orElseThrow(ApiException::notFound);
	}

	private Product productOf(ProductVariant variant) {
		return find(variant.getProductId());
	}

	private void ensureNameFree(String name, UUID exceptId) {
		if (products.nameTaken(bandId(), name, exceptId)) {
			throw nameTaken();
		}
	}

	private void save(Product product) {
		try {
			products.saveAndFlush(product);
		}
		catch (DataIntegrityViolationException duplicate) {
			throw translate(duplicate, null);
		}
	}

	private void save(ProductVariant variant) {
		try {
			variants.saveAndFlush(variant);
		}
		catch (DataIntegrityViolationException duplicate) {
			throw translate(duplicate, null);
		}
	}

	private void flush(Product product) {
		try {
			products.saveAndFlush(product);
		}
		catch (ObjectOptimisticLockingFailureException concurrent) {
			throw versionConflict();
		}
		catch (DataIntegrityViolationException duplicate) {
			throw translate(duplicate, null);
		}
	}

	private void flush(ProductVariant variant) {
		try {
			variants.saveAndFlush(variant);
		}
		catch (ObjectOptimisticLockingFailureException concurrent) {
			throw versionConflict();
		}
		catch (DataIntegrityViolationException duplicate) {
			throw translate(duplicate, null);
		}
	}

	/** The unique indexes are the last word when two admins save the same name or SKU at once. */
	private static RuntimeException translate(DataIntegrityViolationException ex, String field) {
		if (violates(ex, NAME_INDEX)) {
			return nameTaken();
		}
		if (violates(ex, ATTRIBUTES_INDEX)) {
			return variantTaken(field);
		}
		if (violates(ex, SKU_INDEX)) {
			return skuTaken(field);
		}
		return ex;
	}

	private static boolean violates(DataIntegrityViolationException ex, String index) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause.getMessage() != null && cause.getMessage().contains(index)) {
				return true;
			}
		}
		return false;
	}

	private static void requireVersion(long current, long expected) {
		if (current != expected) {
			throw versionConflict();
		}
	}

	private static ApiException nameTaken() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.PRODUCT_NAME_TAKEN,
				"Another product already has this name.", "name");
	}

	private static ApiException variantTaken(String prefix) {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.VARIANT_TAKEN,
				"The product already has a variant with this size and color.", prefixed(prefix, "size"));
	}

	private static ApiException skuTaken(String prefix) {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.SKU_TAKEN, "Another variant already has this SKU.",
				prefixed(prefix, "sku"));
	}

	private static String prefixed(String prefix, String field) {
		return prefix == null ? field : prefix + "." + field;
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
				"Someone else changed this product. Reload it and try again.");
	}

	/** How the unique index compares a size, color or SKU. */
	private static String key(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}

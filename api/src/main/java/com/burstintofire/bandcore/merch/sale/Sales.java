package com.burstintofire.bandcore.merch.sale;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.merch.event.Event;
import com.burstintofire.bandcore.merch.event.EventRepository;
import com.burstintofire.bandcore.merch.event.EventStatus;
import com.burstintofire.bandcore.merch.product.Product;
import com.burstintofire.bandcore.merch.product.ProductRepository;
import com.burstintofire.bandcore.merch.product.ProductVariant;
import com.burstintofire.bandcore.merch.product.ProductVariantRepository;
import com.burstintofire.bandcore.merch.stock.MovementType;
import com.burstintofire.bandcore.merch.stock.StockService;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/** Online sales for the current band. Sale records and stock movements commit together. */
@Service
public class Sales {

	public static final int MAX_ITEMS = 100;

	public static final long MAX_TOTAL_CENTS = 1_000_000_000_000L;

	public record ItemInput(UUID variantId, int quantity, Long discountCents, String discountReason) {
	}

	public record NewSale(UUID id, UUID eventId, PaymentMethod paymentMethod, List<ItemInput> items) {
	}

	public record SaleDetails(Sale sale, List<SaleItem> items) {
	}

	public record Registration(SaleDetails details, boolean created) {
	}

	private final SaleRepository sales;

	private final SaleItemRepository items;

	private final EventRepository events;

	private final ProductVariantRepository variants;

	private final ProductRepository products;

	private final StockService stock;

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final IdGenerator ids;

	private final Clock clock;

	Sales(SaleRepository sales, SaleItemRepository items, EventRepository events, ProductVariantRepository variants,
			ProductRepository products, StockService stock, JdbcTemplate jdbc, AuditRecorder audit, IdGenerator ids,
			Clock clock) {
		this.sales = sales;
		this.items = items;
		this.events = events;
		this.variants = variants;
		this.products = products;
		this.stock = stock;
		this.jdbc = jdbc;
		this.audit = audit;
		this.ids = ids;
		this.clock = clock;
	}

	/**
	 * Registers the sale once per client-generated id. A per-sale transaction lock makes retries
	 * wait for the first request before looking up its payload hash; stock movements use their own
	 * conditional updates, so concurrent sales cannot oversell.
	 */
	@PreAuthorize(Permissions.CAN_WRITE_SALES)
	@Transactional
	public Registration create(NewSale input) {
		UUID bandId = bandId();
		String requestHash = requestHash(input);
		lockIdempotencyKey(bandId, input.id());
		Sale existing = sales.findByBandIdAndId(bandId, input.id()).orElse(null);
		if (existing != null) {
			if (!existing.getRequestHash().equals(requestHash)) {
				throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SALE_IDEMPOTENCY_CONFLICT,
						"This sale id was already used for a different request.");
			}
			return new Registration(details(existing), false);
		}

		List<ItemInput> requested = List.copyOf(input.items());
		Set<UUID> uniqueVariants = new HashSet<>();
		for (int index = 0; index < requested.size(); index++) {
			if (!uniqueVariants.add(requested.get(index).variantId())) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SALE_DUPLICATE_VARIANT,
						"Each variant can appear only once in a sale.", "items[" + index + "].variantId");
			}
		}

		List<ProductVariant> foundVariants = variants.findForSaleByBandIdAndIdIn(bandId,
				requested.stream().map(ItemInput::variantId).toList());
		if (foundVariants.size() != requested.size()) {
			throw ApiException.notFound();
		}
		Map<UUID, ProductVariant> variantById = new LinkedHashMap<>();
		foundVariants.forEach(variant -> variantById.put(variant.getId(), variant));
		List<UUID> productIds = foundVariants.stream().map(ProductVariant::getProductId).distinct().toList();
		List<Product> foundProducts = products.findForSaleByBandIdAndIdIn(bandId, productIds);
		if (foundProducts.size() != productIds.size()) {
			throw ApiException.notFound();
		}
		Map<UUID, Product> productById = new LinkedHashMap<>();
		foundProducts.forEach(product -> productById.put(product.getId(), product));

		for (ProductVariant variant : foundVariants) {
			Product product = productById.get(variant.getProductId());
			if (variant.isArchived() || product.isArchived()) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VARIANT_ARCHIVED,
						"Archived products and variants cannot be sold.", "items");
			}
		}

		if (input.eventId() != null) {
			Event event = events.findForSaleByBandIdAndId(bandId, input.eventId()).orElseThrow(ApiException::notFound);
			if (event.getStatus() == EventStatus.CLOSED) {
				throw new ApiException(HttpStatus.CONFLICT, ErrorCode.EVENT_CLOSED,
						"Sales cannot be registered on a closed event.", "eventId");
			}
		}

		Map<UUID, StockService.Level> levels = stock.lock(uniqueVariants);
		if (levels.size() != uniqueVariants.size()) {
			throw ApiException.notFound();
		}

		Currency currency = null;
		long totalCents = 0;
		List<PreparedItem> prepared = new ArrayList<>(requested.size());
		for (int index = 0; index < requested.size(); index++) {
			ItemInput item = requested.get(index);
			ProductVariant variant = variantById.get(item.variantId());
			Product product = productById.get(variant.getProductId());
			if (currency == null) {
				currency = product.getCurrency();
			}
			else if (currency != product.getCurrency()) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SALE_CURRENCY_MISMATCH,
						"Every item in a sale must use the same currency.", "items[" + index + "].variantId");
			}

			long unitPriceCents = variant.effectivePriceCents(product);
			long grossCents = Math.multiplyExact(unitPriceCents, item.quantity());
			long discountCents = item.discountCents() == null ? 0 : item.discountCents();
			if (discountCents > grossCents) {
				throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
						"The discount cannot exceed the item's price.", "items[" + index + "].discountCents");
			}
			String discountReason = blankToNull(item.discountReason());
			if (discountCents > 0 && discountReason == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
						"A reason is required for a discounted item.", "items[" + index + "].discountReason");
			}
			if (discountCents == 0) {
				discountReason = null;
			}
			totalCents = Math.addExact(totalCents, grossCents - discountCents);
			if (totalCents > MAX_TOTAL_CENTS) {
				throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
						"The sale total exceeds the maximum allowed.", "items");
			}
			prepared.add(new PreparedItem(item, index, unitPriceCents,
					levels.get(item.variantId()).averageUnitCostCents(), discountCents, discountReason));
		}

		UUID sellerUserId = CurrentUser.require().userId();
		var now = clock.instant();
		Sale sale = new Sale(input.id(), bandId, input.eventId(), sellerUserId, now, input.paymentMethod(), currency,
				totalCents, requestHash, now);
		sales.saveAndFlush(sale);

		stock.apply(MovementType.SALE, requested.stream()
				.map(item -> new StockService.Change(item.variantId(), -item.quantity()))
				.toList(), new StockService.Source("SALE", sale.getId()));

		List<SaleItem> savedItems = new ArrayList<>(prepared.size());
		for (PreparedItem item : prepared) {
			savedItems.add(items.save(new SaleItem(ids.newId(), bandId, sale.getId(), item.input().variantId(),
					item.position(), item.input().quantity(), item.unitPriceCents(), item.unitCostCents(),
					item.discountCents(), item.discountReason())));
		}
		items.flush();
		audit.record("sale.created", "sale", sale.getId(), Map.of("event_id",
				sale.getEventId() == null ? "" : sale.getEventId().toString(), "currency", currency.name(),
				"total_cents", totalCents, "item_count", savedItems.size(), "payment_method",
				sale.getPaymentMethod().name()));
		return new Registration(new SaleDetails(sale, savedItems), true);
	}

	private SaleDetails details(Sale sale) {
		return new SaleDetails(sale, items.findByBandIdAndSaleIdOrderByPosition(bandId(), sale.getId()));
	}

	private void lockIdempotencyKey(UUID bandId, UUID saleId) {
		jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", rs -> {
		}, bandId + ":" + saleId);
	}

	private static String requestHash(NewSale input) {
		StringBuilder canonical = new StringBuilder();
		append(canonical, input.eventId());
		append(canonical, input.paymentMethod());
		for (ItemInput item : input.items()) {
			append(canonical, item.variantId());
			append(canonical, item.quantity());
			append(canonical, item.discountCents() == null ? 0 : item.discountCents());
			append(canonical, blankToNull(item.discountReason()));
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
		}
	}

	private static void append(StringBuilder target, Object value) {
		String text = value == null ? "" : value.toString();
		target.append(text.length()).append(':').append(text).append(';');
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

	private record PreparedItem(ItemInput input, int position, long unitPriceCents, Long unitCostCents,
			long discountCents, String discountReason) {
	}

}

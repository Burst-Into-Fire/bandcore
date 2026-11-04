package com.burstintofire.bandcore.merch.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.merch.ProductionBatchRegistered;
import com.burstintofire.bandcore.merch.product.Products;
import com.burstintofire.bandcore.merch.stock.MovementType;
import com.burstintofire.bandcore.merch.stock.StockService;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.money.Allocation;
import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.BandCalendar;

/**
 * Production batches of the current band (M7-03, RF24). Receiving a batch brings its units into
 * stock and updates each variant's weighted average unit cost (ADR-0009). Everyone reads them;
 * admins register, describe and cancel them.
 */
@Service
public class Batches {

	/** One line of a new batch; the unit cost is needed when costs are given per item. */
	public record ItemInput(UUID variantId, int quantity, Long unitCostCents) {
	}

	/**
	 * A new batch; without a received date it is today in the band's time zone. With an expense,
	 * the finance module creates it for the batch's total, in the same transaction.
	 */
	public record NewBatch(String supplier, LocalDate orderedOn, LocalDate receivedOn, Currency currency,
			CostMode costMode, Long totalCostCents, String notes, List<ItemInput> items,
			ProductionBatchRegistered.ExpenseDraft expense) {
	}

	/** A partial update of the description: null leaves a field unchanged; empty texts clear it. */
	public record BatchChanges(long version, String supplier, LocalDate orderedOn, String notes) {
	}

	/** A line with its variant (and the variant's product). */
	public record ItemDetails(BatchItem item, Products.VariantDetails variant) {
	}

	/** @param cancellable still the latest movement of each of its variants */
	public record BatchDetails(ProductionBatch batch, List<ItemDetails> items, boolean cancellable) {
	}

	public static final int MAX_ITEMS = 100;

	private final ProductionBatchRepository batches;

	private final BatchItemRepository items;

	private final Products products;

	private final StockService stock;

	private final BandCalendar calendar;

	private final AuditRecorder audit;

	private final ApplicationEventPublisher events;

	private final IdGenerator ids;

	private final Clock clock;

	Batches(ProductionBatchRepository batches, BatchItemRepository items, Products products, StockService stock,
			BandCalendar calendar, AuditRecorder audit, ApplicationEventPublisher events, IdGenerator ids,
			Clock clock) {
		this.batches = batches;
		this.items = items;
		this.products = products;
		this.stock = stock;
		this.calendar = calendar;
		this.audit = audit;
		this.events = events;
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public Page<BatchDetails> list(int page, int size) {
		Page<ProductionBatch> found = batches.findByBandId(bandId(), PageRequest.of(page, size));
		return new PageImpl<>(details(found.getContent()), found.getPageable(), found.getTotalElements());
	}

	/** Another band's batch answers 404, exactly like one that does not exist. */
	@PreAuthorize(Permissions.CAN_READ_MERCH)
	@Transactional(readOnly = true)
	public BatchDetails get(UUID batchId) {
		return details(List.of(find(batchId))).getFirst();
	}

	/**
	 * Receives a batch: every line's units come into stock (BATCH_IN movements) and each variant's
	 * average unit cost takes them in. Variants of other bands answer 404; archived ones 422
	 * VARIANT_ARCHIVED; a product in another currency 422 BATCH_CURRENCY_MISMATCH. With an
	 * expense, the batch must cost something (422 BATCH_COST_REQUIRED), and an expense the finance
	 * module refuses rolls the whole batch back.
	 */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public BatchDetails register(NewBatch input) {
		List<ItemInput> lines = input.items();
		Map<UUID, Products.VariantDetails> variants = products
			.variants(lines.stream().map(ItemInput::variantId).toList());
		checkItems(input, variants);
		long[] lineCosts = lineCosts(input);
		long total = 0;
		for (long lineCost : lineCosts) {
			total = Math.addExact(total, lineCost);
		}
		if (total > ProductionBatch.MAX_TOTAL_COST_CENTS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
					"The batch costs more than the largest amount allowed.", "items");
		}
		LocalDate receivedOn = input.receivedOn() != null ? input.receivedOn() : calendar.today();
		if (input.orderedOn() != null && input.orderedOn().isAfter(receivedOn)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
					"A batch cannot be ordered after it was received.", "orderedOn");
		}
		if (input.expense() != null && total == 0) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.BATCH_COST_REQUIRED,
					"A batch that costs nothing cannot become an expense.", "expense");
		}

		Instant now = clock.instant();
		ProductionBatch batch = new ProductionBatch(ids.newId(), bandId(), receivedOn, input.currency(),
				input.costMode(), total, input.expense() != null, CurrentUser.require().userId(), now);
		batch.describe(blankToNull(input.supplier()), input.orderedOn(), blankToNull(input.notes()));
		batches.saveAndFlush(batch);

		Map<UUID, StockService.Level> before = stock.lock(variants.keySet());
		List<StockService.Change> changes = lines.stream()
			.map(line -> new StockService.Change(line.variantId(), line.quantity()))
			.toList();
		Map<UUID, StockService.Applied> applied = stock
			.apply(MovementType.BATCH_IN, changes, StockService.Source.batch(batch.getId()))
			.stream()
			.collect(Collectors.toMap(StockService.Applied::variantId, change -> change));
		List<BatchItem> saved = new ArrayList<>();
		for (int position = 0; position < lines.size(); position++) {
			ItemInput line = lines.get(position);
			StockService.Level level = before.get(line.variantId());
			stock.setAverageCost(line.variantId(), WeightedAverageCost.next(level.quantityOnHand(),
					level.averageUnitCostCents(), line.quantity(), lineCosts[position]));
			saved.add(items.save(new BatchItem(ids.newId(), bandId(), batch.getId(), line.variantId(), position,
					line.quantity(), lineCosts[position], level.quantityOnHand(), level.averageUnitCostCents(),
					applied.get(line.variantId()).movementId())));
		}
		items.flush();
		audit.record("production_batch.registered", "production_batch", batch.getId(), snapshot(batch, saved));
		events.publishEvent(new ProductionBatchRegistered(bandId(), batch.getId(), batch.getCurrency(),
				batch.getTotalCostCents(), batch.getReceivedOn(), input.expense()));
		return get(batch.getId());
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public BatchDetails update(UUID batchId, BatchChanges changes) {
		ProductionBatch batch = find(batchId);
		requireVersion(batch, changes.version());
		LocalDate orderedOn = changes.orderedOn() != null ? changes.orderedOn() : batch.getOrderedOn();
		if (orderedOn != null && orderedOn.isAfter(batch.getReceivedOn())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
					"A batch cannot be ordered after it was received.", "orderedOn");
		}
		Map<String, Object> before = batch.details();
		batch.describe(changes.supplier() != null ? blankToNull(changes.supplier()) : batch.getSupplier(), orderedOn,
				changes.notes() != null ? blankToNull(changes.notes()) : batch.getNotes());
		Map<String, Object> diff = AuditRecorder.diff(before, batch.details());
		if (!diff.isEmpty()) {
			batch.touch(clock.instant());
			flush(batch);
			audit.record("production_batch.updated", "production_batch", batch.getId(), diff);
		}
		return get(batch.getId());
	}

	/**
	 * Takes the batch's units back out of stock and restores each variant's average, exactly as
	 * before the batch. Only while the batch is still the latest movement of each of its variants
	 * (409 BATCH_HAS_LATER_MOVEMENTS); afterwards, correct the stock with an adjustment.
	 */
	@PreAuthorize(Permissions.CAN_MANAGE_MERCH)
	@Transactional
	public BatchDetails cancel(UUID batchId, long version) {
		ProductionBatch batch = find(batchId);
		requireVersion(batch, version);
		if (batch.getStatus() == BatchStatus.CANCELLED) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BATCH_CANCELLED, "This batch is already cancelled.");
		}
		List<BatchItem> lines = items.findByBandIdAndBatchIdIn(bandId(), List.of(batch.getId()));
		Map<UUID, StockService.Level> levels = stock.lock(lines.stream().map(BatchItem::getVariantId).toList());
		for (BatchItem line : lines) {
			if (!line.getMovementId().equals(levels.get(line.getVariantId()).lastMovementId())) {
				throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BATCH_HAS_LATER_MOVEMENTS,
						"Stock of this batch's variants moved after it. Correct it with an adjustment instead.");
			}
		}
		stock.apply(MovementType.BATCH_REVERSAL,
				lines.stream().map(line -> new StockService.Change(line.getVariantId(), -line.getQuantity())).toList(),
				StockService.Source.batch(batch.getId()));
		for (BatchItem line : lines) {
			stock.setAverageCost(line.getVariantId(), line.getAverageCostBeforeCents());
		}
		batch.cancel(CurrentUser.require().userId(), clock.instant());
		flush(batch);
		audit.record("production_batch.cancelled", "production_batch", batch.getId(),
				Map.of("total_cost_cents", batch.getTotalCostCents()));
		return get(batch.getId());
	}

	private void checkItems(NewBatch input, Map<UUID, Products.VariantDetails> variants) {
		Set<UUID> seen = new HashSet<>();
		for (int i = 0; i < input.items().size(); i++) {
			UUID variantId = input.items().get(i).variantId();
			String field = "items[" + i + "].variantId";
			if (!seen.add(variantId)) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.BATCH_DUPLICATE_VARIANT,
						"Each variant appears once in a batch.", field);
			}
			Products.VariantDetails variant = variants.get(variantId);
			if (variant == null) {
				throw ApiException.notFound();
			}
			if (variant.variant().isArchived() || variant.product().isArchived()) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VARIANT_ARCHIVED,
						"Archived products and variants cannot receive stock.", field);
			}
			if (variant.product().getCurrency() != input.currency()) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.BATCH_CURRENCY_MISMATCH,
						"Every product in a batch must be in the batch's currency.", field);
			}
		}
	}

	/** What each line cost: quantity × unit cost, or its share of the total by quantity. */
	private static long[] lineCosts(NewBatch input) {
		List<ItemInput> lines = input.items();
		long[] costs = new long[lines.size()];
		if (input.costMode() == CostMode.ALLOCATED) {
			if (input.totalCostCents() == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
						"A batch with an allocated cost needs its total.", "totalCostCents");
			}
			long[] quantities = lines.stream().mapToLong(ItemInput::quantity).toArray();
			return Allocation.byWeights(input.totalCostCents(), quantities);
		}
		for (int i = 0; i < lines.size(); i++) {
			ItemInput line = lines.get(i);
			if (line.unitCostCents() == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
						"Each item needs its unit cost.", "items[" + i + "].unitCostCents");
			}
			costs[i] = Math.multiplyExact((long) line.quantity(), line.unitCostCents());
		}
		return costs;
	}

	private List<BatchDetails> details(List<ProductionBatch> found) {
		if (found.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<BatchItem>> lines = items
			.findByBandIdAndBatchIdIn(bandId(), found.stream().map(ProductionBatch::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(BatchItem::getBatchId, LinkedHashMap::new, Collectors.toList()));
		Map<UUID, Products.VariantDetails> variants = products.variants(
				lines.values().stream().flatMap(List::stream).map(BatchItem::getVariantId).distinct().toList());
		List<BatchDetails> details = new ArrayList<>();
		for (ProductionBatch batch : found) {
			List<BatchItem> own = lines.getOrDefault(batch.getId(), List.of());
			boolean latest = batch.getStatus() == BatchStatus.RECEIVED && own.stream().allMatch(line -> {
				StockService.Level level = variants.get(line.getVariantId()).stock();
				return level != null && line.getMovementId().equals(level.lastMovementId());
			});
			details.add(new BatchDetails(batch,
					own.stream().map(line -> new ItemDetails(line, variants.get(line.getVariantId()))).toList(), latest));
		}
		return details;
	}

	private static Map<String, Object> snapshot(ProductionBatch batch, List<BatchItem> lines) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("received_on", batch.getReceivedOn().toString());
		snapshot.put("currency", batch.getCurrency().name());
		snapshot.put("cost_mode", batch.getCostMode().name());
		snapshot.put("total_cost_cents", batch.getTotalCostCents());
		snapshot.put("expense_requested", batch.isExpenseRequested());
		snapshot.putAll(batch.details());
		snapshot.put("items", lines.stream().map(line -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("variant_id", line.getVariantId().toString());
			item.put("quantity", line.getQuantity());
			item.put("line_cost_cents", line.getLineCostCents());
			return item;
		}).toList());
		return snapshot;
	}

	private ProductionBatch find(UUID batchId) {
		return batches.findByBandIdAndId(bandId(), batchId).orElseThrow(ApiException::notFound);
	}

	private static void requireVersion(ProductionBatch batch, long version) {
		if (batch.getVersion() != version) {
			throw versionConflict();
		}
	}

	private void flush(ProductionBatch batch) {
		try {
			batches.saveAndFlush(batch);
		}
		catch (ObjectOptimisticLockingFailureException concurrent) {
			throw versionConflict();
		}
	}

	private static ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
				"Someone else changed this batch. Reload it and try again.");
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}

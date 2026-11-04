package com.burstintofire.bandcore.merch.batch;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.merch.ProductionBatchRegistered;
import com.burstintofire.bandcore.merch.product.ProductVariant;
import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.web.PageResponse;

/** Production batches of the current band: merch received at a cost, in cents of the batch's currency. */
@RestController
@RequestMapping("/batches")
@Tag(name = "Production batches")
class BatchController {

	static final int MAX_PAGE = 10_000;

	static final int MAX_SIZE = 100;

	/** The limits of any expense (finance checks them too). */
	static final int MAX_EXPENSE_DESCRIPTION_LENGTH = 120;

	static final int MAX_EXPENSE_NOTES_LENGTH = 500;

	private final Batches batches;

	BatchController(Batches batches) {
		this.batches = batches;
	}

	@GetMapping
	@Operation(operationId = "listBatches", summary = "Production batches of the current band, latest received first",
			description = "Pages start at 0.")
	PageResponse<BatchResponse> list(@RequestParam(defaultValue = "0") @PositiveOrZero @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SIZE) int size) {
		return PageResponse.of(batches.list(page, size), BatchResponse::of);
	}

	@GetMapping("/{batchId}")
	@Operation(operationId = "getBatch", summary = "One production batch of the current band")
	BatchResponse get(@PathVariable UUID batchId) {
		return BatchResponse.of(batches.get(batchId));
	}

	@PostMapping
	@Operation(operationId = "registerBatch", summary = "Receive a production batch (admins)",
			description = "Its units come into stock and each variant's average unit cost takes them in. PER_ITEM "
					+ "needs every item's unit cost; ALLOCATED needs the total, split by quantity. Without receivedOn, "
					+ "it is today in the band's time zone. 422 VARIANT_ARCHIVED, BATCH_CURRENCY_MISMATCH (every "
					+ "product must be in the batch's currency) or BATCH_DUPLICATE_VARIANT. With expense, the batch's "
					+ "total also becomes a split expense, saved together with the batch: the expense's errors (for "
					+ "example SPLIT_SUM_MISMATCH or MEMBER_INACTIVE) point at expense.* fields and nothing is saved; "
					+ "422 BATCH_COST_REQUIRED for a batch that costs nothing.")
	ResponseEntity<BatchResponse> register(@Valid @RequestBody RegisterBatchRequest request) {
		Batches.BatchDetails registered = batches.register(new Batches.NewBatch(request.supplier(),
				request.orderedOn(), request.receivedOn(), request.currency(), request.costMode(),
				request.totalCostCents(), request.notes(),
				request.items()
					.stream()
					.map(item -> new Batches.ItemInput(item.variantId(), item.quantity(), item.unitCostCents()))
					.toList(),
				BatchExpenseRequest.toDraft(request.expense())));
		return ResponseEntity.created(URI.create("/api/batches/" + registered.batch().getId()))
			.body(BatchResponse.of(registered));
	}

	@PatchMapping("/{batchId}")
	@Operation(operationId = "updateBatch", summary = "Change a batch's supplier, order date or notes (admins)",
			description = "Quantities and costs never change. Send the version that was read: 409 VERSION_CONFLICT "
					+ "when it changed meanwhile. Omitted fields are left unchanged; empty texts clear them.")
	BatchResponse update(@PathVariable UUID batchId, @Valid @RequestBody UpdateBatchRequest request) {
		return BatchResponse.of(batches.update(batchId, new Batches.BatchChanges(request.version(),
				request.supplier(), request.orderedOn(), request.notes())));
	}

	@PostMapping("/{batchId}/cancel")
	@Operation(operationId = "cancelBatch", summary = "Cancel a batch (admins)",
			description = "Takes its units back out of stock and restores each variant's average cost. Only while it "
					+ "is the latest stock movement of each of its variants (see cancellable): 409 "
					+ "BATCH_HAS_LATER_MOVEMENTS otherwise. An expense created from the batch does not change.")
	BatchResponse cancel(@PathVariable UUID batchId, @Valid @RequestBody CancelBatchRequest request) {
		return BatchResponse.of(batches.cancel(batchId, request.version()));
	}

	record BatchItemRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID variantId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive @Max(
					BatchItem.MAX_QUANTITY) Integer quantity,
			@Schema(description = "Needed when costMode is PER_ITEM") @PositiveOrZero @Max(
					BatchItem.MAX_UNIT_COST_CENTS) Long unitCostCents) {
	}

	record RegisterBatchRequest(@Size(max = ProductionBatch.MAX_SUPPLIER_LENGTH) String supplier,
			LocalDate orderedOn,
			@Schema(description = "Defaults to today in the band's time zone") LocalDate receivedOn,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Every product in the batch must be in it") @NotNull Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull CostMode costMode,
			@Schema(description = "Needed when costMode is ALLOCATED; computed otherwise") @PositiveOrZero @Max(
					ProductionBatch.MAX_TOTAL_COST_CENTS) Long totalCostCents,
			@Size(max = ProductionBatch.MAX_NOTES_LENGTH) String notes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(
					max = Batches.MAX_ITEMS) @Valid List<BatchItemRequest> items,
			@Schema(description = "Also create an expense for the batch's total, split among members") @Valid
			BatchExpenseRequest expense) {
	}

	/** The same split as an expense's; members, categories and amounts are checked by the finance module. */
	record BatchExpenseRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(
					max = MAX_EXPENSE_DESCRIPTION_LENGTH) String description,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Usually the band's default merch category") @NotNull UUID categoryId,
			@Schema(description = "Defaults to the batch's received date") LocalDate expenseDate,
			@Schema(description = "Paid already (needs paidByMemberId); planned when omitted") Boolean paid,
			UUID paidByMemberId, @Schema(description = "When paid; defaults to the expense date") LocalDate paidOn,
			@Size(max = MAX_EXPENSE_NOTES_LENGTH) String notes,
			@Schema(description = "By default equally among every active member") @Valid BatchSplitRequest split) {

		static ProductionBatchRegistered.ExpenseDraft toDraft(BatchExpenseRequest request) {
			if (request == null) {
				return null;
			}
			BatchSplitRequest split = request.split();
			return new ProductionBatchRegistered.ExpenseDraft(request.description(), request.categoryId(),
					request.expenseDate(), Boolean.TRUE.equals(request.paid()), request.paidByMemberId(), request.paidOn(),
					request.notes(),
					split == null ? ProductionBatchRegistered.ExpenseSplitMode.EQUAL : split.mode(),
					split == null || split.shares() == null ? null
							: split.shares()
								.stream()
								.map(share -> new ProductionBatchRegistered.Share(share.memberId(), share.amountCents(),
										share.basisPoints()))
								.toList());
		}

	}

	record BatchSplitRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ProductionBatchRegistered.ExpenseSplitMode mode,
			@Schema(description = "EQUAL: the members (none means every active member); CUSTOM_AMOUNTS: amountCents "
					+ "per member; CUSTOM_PERCENTAGES: basisPoints per member") @Size(
							max = 100) List<@Valid @NotNull BatchShareRequest> shares) {
	}

	record BatchShareRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID memberId,
			@Schema(description = "CUSTOM_AMOUNTS only") @PositiveOrZero @Max(
					ProductionBatch.MAX_TOTAL_COST_CENTS) Long amountCents,
			@Schema(description = "CUSTOM_PERCENTAGES only; 10000 is 100%") @Min(0) @Max(10_000) Long basisPoints) {
	}

	record UpdateBatchRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The version that was read") @NotNull @PositiveOrZero Long version,
			@Schema(description = "Empty clears it") @Size(max = ProductionBatch.MAX_SUPPLIER_LENGTH) String supplier,
			LocalDate orderedOn,
			@Schema(description = "Empty clears them") @Size(max = ProductionBatch.MAX_NOTES_LENGTH) String notes) {
	}

	record CancelBatchRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The version that was read") @NotNull @PositiveOrZero Long version) {
	}

	record BatchItemResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID variantId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productName, String size, String color,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "What the line cost; the lines add up to the batch total") long lineCostCents) {

		static BatchItemResponse of(Batches.ItemDetails details) {
			BatchItem item = details.item();
			ProductVariant variant = details.variant().variant();
			return new BatchItemResponse(item.getId(), item.getVariantId(), variant.getProductId(),
					details.variant().product().getName(), variant.getSize(), variant.getColor(), item.getQuantity(),
					item.getLineCostCents());
		}

	}

	record BatchResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id, String supplier,
			LocalDate orderedOn, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate receivedOn,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) CostMode costMode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalCostCents, String notes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BatchStatus status, Instant cancelledAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Registering it also created an expense, which cancelling does not change") boolean expenseRequested,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Still the latest movement of each of its variants, so it can be cancelled") boolean cancellable,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Send it back when changing or cancelling the batch") long version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BatchItemResponse> items,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

		static BatchResponse of(Batches.BatchDetails details) {
			ProductionBatch batch = details.batch();
			return new BatchResponse(batch.getId(), batch.getSupplier(), batch.getOrderedOn(), batch.getReceivedOn(),
					batch.getCurrency(), batch.getCostMode(), batch.getTotalCostCents(), batch.getNotes(),
					batch.getStatus(), batch.getCancelledAt(), batch.isExpenseRequested(), details.cancellable(),
					batch.getVersion(),
					details.items().stream().map(BatchItemResponse::of).toList(), batch.getCreatedAt());
		}

	}

}

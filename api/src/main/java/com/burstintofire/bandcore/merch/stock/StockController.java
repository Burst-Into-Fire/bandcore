package com.burstintofire.bandcore.merch.stock;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.web.PageResponse;

/** Stock movements of the current band's variants. */
@RestController
@RequestMapping("/variants")
@Tag(name = "Stock")
class StockController {

	static final int MAX_PAGE = 10_000;

	static final int MAX_SIZE = 100;

	private final StockService stock;

	StockController(StockService stock) {
		this.stock = stock;
	}

	@GetMapping("/{variantId}/movements")
	@Operation(operationId = "listStockMovements", summary = "Stock movements of a variant, newest first",
			description = "Every change to the quantity on hand, with what caused it. Pages start at 0.")
	PageResponse<StockMovementResponse> movements(@PathVariable UUID variantId,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(MAX_SIZE) int size) {
		return PageResponse.of(stock.movements(variantId, page, size), StockMovementResponse::of);
	}

	record StockMovementResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) MovementType type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Units in (positive) or out (negative)") int quantityDelta,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Quantity on hand right after the movement") int quantityAfter,
			@Schema(description = "What caused it, for example BATCH") String sourceType, UUID sourceId,
			@Schema(description = "Why an adjustment was made") String reason, String note,
			@Schema(description = "Who made it, when it was a person") UUID actorUserId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt) {

		static StockMovementResponse of(StockService.Movement movement) {
			return new StockMovementResponse(movement.id(), movement.type(), movement.quantityDelta(),
					movement.quantityAfter(), movement.sourceType(), movement.sourceId(), movement.reason(),
					movement.note(), movement.actorUserId(), movement.occurredAt());
		}

	}

}

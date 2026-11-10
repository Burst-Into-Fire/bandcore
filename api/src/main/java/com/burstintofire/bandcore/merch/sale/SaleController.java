package com.burstintofire.bandcore.merch.sale;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.money.Currency;

/** Online merch sales of the current band. */
@RestController
@RequestMapping("/sales")
@Tag(name = "Sales")
class SaleController {

	private final Sales sales;

	SaleController(Sales sales) {
		this.sales = sales;
	}

	@PostMapping
	@Operation(operationId = "createSale", summary = "Register a sale and decrement stock atomically",
			description = "The client supplies an idempotency UUID. Repeating the same payload returns the sale; "
					+ "reusing its id with a different payload returns 409. All items must use one currency.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Existing sale returned for an idempotent retry",
				useReturnTypeSchema = true),
		@ApiResponse(responseCode = "201", description = "Sale registered", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "409", description = "Idempotency key reused with a different payload")
	})
	ResponseEntity<SaleResponse> create(@Valid @RequestBody CreateSaleRequest request) {
		Sales.NewSale input = new Sales.NewSale(request.id(), request.eventId(), request.paymentMethod(),
				request.items().stream().map(item -> new Sales.ItemInput(item.variantId(), item.quantity(),
						item.discountCents(), item.discountReason())).toList());
		Sales.Registration registration = sales.create(input);
		Sales.SaleDetails details = registration.details();
		SaleResponse response = SaleResponse.of(details);
		if (registration.created()) {
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		}
		return ResponseEntity.ok(response);
	}

	record CreateSaleRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Client-generated idempotency UUID")
			@NotNull UUID id,
			UUID eventId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull PaymentMethod paymentMethod,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Size(min = 1, max = Sales.MAX_ITEMS)
			List<@NotNull @Valid SaleItemRequest> items) {
	}

	record SaleItemRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID variantId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive @Max(MAX_QUANTITY)
			Integer quantity,
			@Schema(description = "Optional total discount for this line in integer cents")
			@PositiveOrZero @Max(Sales.MAX_TOTAL_CENTS) Long discountCents,
			@Size(max = 200) String discountReason) {
	}

	record SaleResponse(UUID id, UUID eventId, UUID sellerUserId, Instant soldAt, PaymentMethod paymentMethod,
			Currency currency, long totalCents, SaleStatus status, SaleSource source, Instant createdAt,
			List<SaleItemResponse> items) {

		static SaleResponse of(Sales.SaleDetails details) {
			Sale sale = details.sale();
			return new SaleResponse(sale.getId(), sale.getEventId(), sale.getSellerUserId(), sale.getSoldAt(),
					sale.getPaymentMethod(), sale.getCurrency(), sale.getTotalCents(), sale.getStatus(), sale.getSource(),
					sale.getCreatedAt(), details.items().stream().map(SaleItemResponse::of).toList());
		}

	}

	record SaleItemResponse(UUID id, UUID variantId, int position, int quantity, long unitPriceCents,
			Long unitCostCents, long discountCents, String discountReason) {

		static SaleItemResponse of(SaleItem item) {
			return new SaleItemResponse(item.getId(), item.getVariantId(), item.getPosition(), item.getQuantity(),
					item.getUnitPriceCents(), item.getUnitCostCents(), item.getDiscountCents(),
					item.getDiscountReason());
		}

	}

	private static final int MAX_QUANTITY = 1_000_000;

}

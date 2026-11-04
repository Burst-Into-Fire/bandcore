package com.burstintofire.bandcore.merch.product;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.merch.product.ProductController.ProductVariantRequest;
import com.burstintofire.bandcore.merch.product.ProductController.ProductVariantResponse;

/** Variants of the current band's products, addressed by their own id. */
@RestController
@RequestMapping("/variants")
@Tag(name = "Products")
class VariantController {

	private final Products products;

	VariantController(Products products) {
		this.products = products;
	}

	@PutMapping("/{variantId}")
	@Operation(operationId = "replaceVariant", summary = "Change a variant (admins): send every value",
			description = "Send the version that was read: 409 VERSION_CONFLICT when it changed meanwhile. A null "
					+ "price sells at the product's price. 409 VARIANT_TAKEN (same size and color) or SKU_TAKEN.")
	ProductVariantResponse replace(@PathVariable UUID variantId, @Valid @RequestBody ReplaceVariantRequest request) {
		Products.VariantDetails details = products.replaceVariant(variantId, request.version(),
				new Products.VariantInput(request.size(), request.color(), request.sku(), request.priceCents(),
						request.lowStockThreshold() == null ? 0 : request.lowStockThreshold()));
		return ProductVariantResponse.of(details.product(), details.variant());
	}

	@PostMapping("/{variantId}/archive")
	@Operation(operationId = "archiveVariant",
			summary = "Archive a variant (admins): it stays on past records but can no longer be sold")
	ProductVariantResponse archive(@PathVariable UUID variantId) {
		Products.VariantDetails details = products.archiveVariant(variantId);
		return ProductVariantResponse.of(details.product(), details.variant());
	}

	@PostMapping("/{variantId}/unarchive")
	@Operation(operationId = "unarchiveVariant", summary = "Bring an archived variant back (admins)")
	ProductVariantResponse unarchive(@PathVariable UUID variantId) {
		Products.VariantDetails details = products.unarchiveVariant(variantId);
		return ProductVariantResponse.of(details.product(), details.variant());
	}

	@DeleteMapping("/{variantId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "deleteVariant", summary = "Delete a variant that never moved stock (admins)",
			description = "409 VARIANT_HAS_HISTORY otherwise (archive it instead); 409 LAST_VARIANT for the only "
					+ "variant of a product (delete the product instead).")
	void delete(@PathVariable UUID variantId,
			@Parameter(description = "The version that was read; 409 VERSION_CONFLICT when it changed")
			@RequestParam(required = false) Long version) {
		products.deleteVariant(variantId, version);
	}

	static Products.VariantInput input(ProductVariantRequest request) {
		return new Products.VariantInput(request.size(), request.color(), request.sku(), request.priceCents(),
				request.lowStockThreshold() == null ? 0 : request.lowStockThreshold());
	}

	record ReplaceVariantRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The version that was read") @NotNull @PositiveOrZero Long version,
			@Schema(description = "Empty for none") @Size(max = ProductVariant.MAX_SIZE_LENGTH) String size,
			@Schema(description = "Empty for none") @Size(max = ProductVariant.MAX_COLOR_LENGTH) String color,
			@Schema(description = "Empty for none") @Size(max = ProductVariant.MAX_SKU_LENGTH) String sku,
			@Schema(description = "Null sells at the product's price") @PositiveOrZero @Max(
					Product.MAX_PRICE_CENTS) Long priceCents,
			@Schema(description = "Low stock at or below this quantity; 0 by default") @PositiveOrZero @Max(
					ProductVariant.MAX_LOW_STOCK_THRESHOLD) Integer lowStockThreshold) {
	}

}

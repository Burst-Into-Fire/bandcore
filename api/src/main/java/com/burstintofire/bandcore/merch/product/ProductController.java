package com.burstintofire.bandcore.merch.product;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.merch.stock.StockService;
import com.burstintofire.bandcore.shared.money.Currency;

/** The merch catalog of the current band: products and their variants, prices in cents. */
@RestController
@RequestMapping("/products")
@Tag(name = "Products")
class ProductController {

	static final int MAX_VARIANTS = 50;

	private final Products products;

	ProductController(Products products) {
		this.products = products;
	}

	@GetMapping
	@Operation(operationId = "listProducts", summary = "Products of the current band by name, with their variants",
			description = "ACTIVE leaves out archived products and variants (what sales offer); ALL is the whole catalog.")
	List<ProductResponse> list(@RequestParam(defaultValue = "ALL") Products.Listing status) {
		return products.list(status).stream().map(ProductResponse::of).toList();
	}

	@GetMapping("/{productId}")
	@Operation(operationId = "getProduct", summary = "One product of the current band, with all its variants")
	ProductResponse get(@PathVariable UUID productId) {
		return ProductResponse.of(products.get(productId));
	}

	@PostMapping
	@Operation(operationId = "createProduct", summary = "Create a product with its variants (admins)",
			description = "Without variants, the product gets one default variant (no size, no color). "
					+ "409 PRODUCT_NAME_TAKEN, VARIANT_TAKEN (same size and color) or SKU_TAKEN.")
	ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
		Products.ProductDetails created = products.create(new Products.NewProduct(request.name(), request.description(),
				request.currency(), request.defaultPriceCents(),
				request.variants() == null ? List.of() : request.variants().stream().map(VariantController::input).toList()));
		return ResponseEntity.created(URI.create("/api/products/" + created.product().getId()))
			.body(ProductResponse.of(created));
	}

	@PatchMapping("/{productId}")
	@Operation(operationId = "updateProduct", summary = "Change a product (admins)",
			description = "Send the version that was read: 409 VERSION_CONFLICT when it changed meanwhile. Omitted "
					+ "fields are left unchanged; an empty description clears it.")
	ProductResponse update(@PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
		return ProductResponse.of(products.update(productId, new Products.ProductChanges(request.version(),
				request.name(), request.description(), request.currency(), request.defaultPriceCents())));
	}

	@PostMapping("/{productId}/archive")
	@Operation(operationId = "archiveProduct",
			summary = "Archive a product (admins): it stays on past records but can no longer be sold")
	ProductResponse archive(@PathVariable UUID productId) {
		return ProductResponse.of(products.archive(productId));
	}

	@PostMapping("/{productId}/unarchive")
	@Operation(operationId = "unarchiveProduct", summary = "Bring an archived product back (admins)")
	ProductResponse unarchive(@PathVariable UUID productId) {
		return ProductResponse.of(products.unarchive(productId));
	}

	@DeleteMapping("/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "deleteProduct", summary = "Delete a product whose variants never moved stock (admins)",
			description = "409 PRODUCT_HAS_HISTORY otherwise: archive it instead.")
	void delete(@PathVariable UUID productId,
			@Parameter(description = "The version that was read; 409 VERSION_CONFLICT when it changed")
			@RequestParam(required = false) Long version) {
		products.delete(productId, version);
	}

	@PostMapping("/{productId}/variants")
	@Operation(operationId = "addProductVariant", summary = "Add a variant to a product (admins)",
			description = "409 VARIANT_TAKEN (same size and color) or SKU_TAKEN.")
	ResponseEntity<ProductVariantResponse> addVariant(@PathVariable UUID productId,
			@Valid @RequestBody ProductVariantRequest request) {
		Products.VariantDetails created = products.addVariant(productId, VariantController.input(request));
		return ResponseEntity.created(URI.create("/api/variants/" + created.variant().getId()))
			.body(ProductVariantResponse.of(created));
	}

	record CreateProductRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(
					max = Product.MAX_NAME_LENGTH) String name,
			@Size(max = Product.MAX_DESCRIPTION_LENGTH) String description,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Of every price and cost of the product") @NotNull Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @PositiveOrZero @Max(
					Product.MAX_PRICE_CENTS) Long defaultPriceCents,
			@Schema(description = "In display order; empty creates one default variant") @Valid @Size(
					max = MAX_VARIANTS) List<ProductVariantRequest> variants) {
	}

	record UpdateProductRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The version that was read") @NotNull @PositiveOrZero Long version,
			@Size(min = 1, max = Product.MAX_NAME_LENGTH) @Pattern(regexp = ".*\\S.*") String name,
			@Schema(description = "Empty clears it") @Size(max = Product.MAX_DESCRIPTION_LENGTH) String description,
			Currency currency, @PositiveOrZero @Max(Product.MAX_PRICE_CENTS) Long defaultPriceCents) {
	}

	record ProductVariantRequest(
			@Schema(description = "Empty for none", example = "M") @Size(max = ProductVariant.MAX_SIZE_LENGTH) String size,
			@Schema(description = "Empty for none") @Size(max = ProductVariant.MAX_COLOR_LENGTH) String color,
			@Schema(description = "Optional, unique in the band") @Size(max = ProductVariant.MAX_SKU_LENGTH) String sku,
			@Schema(description = "Omit to sell at the product's price") @PositiveOrZero @Max(
					Product.MAX_PRICE_CENTS) Long priceCents,
			@Schema(description = "Low stock at or below this quantity; 0 by default") @PositiveOrZero @Max(
					ProductVariant.MAX_LOW_STOCK_THRESHOLD) Integer lowStockThreshold) {
	}

	record ProductVariantResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId, String size, String color, String sku,
			@Schema(description = "The variant's own price; null sells at the product's") Long priceCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "What a sale charges: the variant's price or the product's") long effectivePriceCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lowStockThreshold,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantityOnHand,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Quantity on hand at or below the low-stock threshold") boolean lowStock,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean archived,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Send it back when changing the variant") long version) {

		static ProductVariantResponse of(Products.VariantDetails details) {
			return of(details.product(), details.variant(), details.stock());
		}

		static ProductVariantResponse of(Product product, ProductVariant variant, StockService.Level stock) {
			int onHand = stock == null ? 0 : stock.quantityOnHand();
			return new ProductVariantResponse(variant.getId(), variant.getProductId(), variant.getSize(),
					variant.getColor(), variant.getSku(), variant.getPriceCents(), variant.effectivePriceCents(product),
					variant.getLowStockThreshold(), onHand, onHand <= variant.getLowStockThreshold(), variant.isArchived(),
					variant.getVersion());
		}

	}

	record ProductResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String description,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long defaultPriceCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean archived,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Send it back when changing the product") long version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "In display order") List<ProductVariantResponse> variants,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

		static ProductResponse of(Products.ProductDetails details) {
			Product product = details.product();
			return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
					product.getCurrency(), product.getDefaultPriceCents(), product.isArchived(), product.getVersion(),
					details.variants()
						.stream()
						.map(variant -> ProductVariantResponse.of(product, variant, details.stockOf(variant)))
						.toList(),
					product.getCreatedAt());
		}

	}

}

package com.burstintofire.bandcore.tenancy.creation;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.web.CentralEndpoint;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;

@RestController
@RequestMapping("/bands")
@CentralEndpoint
@Tag(name = "Bands")
class BandCreationController {

	private final BandCreationService creation;

	private final BandRegistry registry;

	BandCreationController(BandCreationService creation, BandRegistry registry) {
		this.creation = creation;
		this.registry = registry;
	}

	@PostMapping
	@Operation(operationId = "createBand", summary = "Create a band; the caller becomes its first admin",
			description = "Needs a verified email. Answers with the band host URL to log in to.")
	ResponseEntity<CreatedBandResponse> create(@Valid @RequestBody CreateBandRequest request) {
		BandCreationService.CreatedBand created = creation.create(request.name(), request.slug());
		return ResponseEntity.created(URI.create(created.url()))
			.body(new CreatedBandResponse(created.band().getId(), created.band().getSlug(), created.band().getName(),
					created.url()));
	}

	@GetMapping("/slug-availability")
	@Operation(operationId = "checkSlugAvailability", summary = "Check whether a band address can be used")
	SlugAvailabilityResponse slugAvailability(@RequestParam @NotNull @Size(max = 64) String slug) {
		BandRegistry.Availability availability = registry.availability(slug);
		return new SlugAvailabilityResponse(availability.slug(), availability.available(), availability.reason());
	}

	record CreateBandRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80) @NotBlank @Size(max = BandRegistry.MAX_NAME_LENGTH) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 3, maxLength = 32, example = "the-burst") @NotBlank @Size(max = 64) String slug) {
	}

	record CreatedBandResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "https://the-burst.bandcore.app") String url) {
	}

	record SlugAvailabilityResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Normalized slug") String slug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean available,
			@Schema(description = "Why it cannot be used", allowableValues = { "SLUG_INVALID", "SLUG_RESERVED", "SLUG_TAKEN" }) ErrorCode reason) {
	}

}

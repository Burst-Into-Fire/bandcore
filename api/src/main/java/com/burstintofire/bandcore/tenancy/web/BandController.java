package com.burstintofire.bandcore.tenancy.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.web.PublicEndpoint;
import com.burstintofire.bandcore.tenancy.band.Band;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;
import com.burstintofire.bandcore.tenancy.band.BandSettings;

/** The band of the current host. */
@RestController
@RequestMapping("/band")
@Tag(name = "Band")
class BandController {

	private final BandSettings settings;

	BandController(BandSettings settings) {
		this.settings = settings;
	}

	@GetMapping
	@Operation(operationId = "getBand", summary = "The current band")
	BandResponse get() {
		return BandResponse.of(settings.current());
	}

	@PatchMapping
	@Operation(operationId = "updateBand", summary = "Change the band's name or time zone (admins)")
	BandResponse update(@Valid @RequestBody UpdateBandRequest request) {
		return BandResponse.of(settings.update(request.name(), request.timezone()));
	}

	@GetMapping("/public-profile")
	@PublicEndpoint("the band host's login page shows the band name; the slug is already public in the host")
	@Operation(operationId = "getBandPublicProfile", summary = "Name of the band of this host, for the login page")
	BandPublicProfile publicProfile() {
		Band band = settings.publicProfile();
		return new BandPublicProfile(band.getSlug(), band.getName());
	}

	record BandResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "BRL") String currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "America/Sao_Paulo") String timezone) {

		static BandResponse of(Band band) {
			return new BandResponse(band.getId(), band.getSlug(), band.getName(), band.getCurrency(), band.getTimezone());
		}

	}

	record UpdateBandRequest(
			@Size(min = 1, max = BandRegistry.MAX_NAME_LENGTH) @Pattern(regexp = ".*\\S.*") String name,
			@Size(max = 64) String timezone) {
	}

	record BandPublicProfile(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
	}

}

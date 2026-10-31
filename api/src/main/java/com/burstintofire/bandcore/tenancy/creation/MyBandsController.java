package com.burstintofire.bandcore.tenancy.creation;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.web.CentralEndpoint;

@RestController
@CentralEndpoint
@Tag(name = "Bands")
class MyBandsController {

	private final MyBandsService myBands;

	MyBandsController(MyBandsService myBands) {
		this.myBands = myBands;
	}

	@GetMapping("/me/bands")
	@Operation(operationId = "listMyBands", summary = "Bands the current user is an active member of")
	List<MyBandResponse> myBands() {
		return myBands.list()
			.stream()
			.map(band -> new MyBandResponse(band.id(), band.slug(), band.name(), band.role(), band.url()))
			.toList();
	}

	record MyBandResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "ADMIN", "MEMBER", "READ_ONLY" }) String role,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url) {
	}

}

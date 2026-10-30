package com.burstintofire.bandcore.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness check reachable through the web proxy ({@code GET /api/status}). The
 * detailed health endpoints stay on the management port.
 */
@RestController
@Tag(name = "Status")
class StatusController {

	@GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "getStatus", summary = "Check that the API is reachable")
	StatusResponse status() {
		return new StatusResponse("UP");
	}

	record StatusResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "UP") String status) {
	}

}

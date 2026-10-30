package com.burstintofire.bandcore.shared.tenant;

import java.util.Objects;
import java.util.UUID;

/**
 * The band a request or a job works for. Created only from a validated source: the band
 * resolved from the request host, or an explicit choice of a background job.
 */
public record TenantContext(UUID bandId, String slug) {

	public TenantContext {
		Objects.requireNonNull(bandId, "bandId");
		Objects.requireNonNull(slug, "slug");
	}

}

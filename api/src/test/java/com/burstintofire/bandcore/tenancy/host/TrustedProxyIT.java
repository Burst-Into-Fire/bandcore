package com.burstintofire.bandcore.tenancy.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;

/** The test client connects from the loopback address, configured here as the trusted proxy. */
@Import(TenantResolutionIT.TestEndpoints.class)
@TestPropertySource(properties = "bandcore.platform.trusted-proxies=127.0.0.1,0:0:0:0:0:0:0:1")
class TrustedProxyIT extends IntegrationTest {

	@Autowired
	BandRegistry bands;

	@Test
	void forwardedHostFromTheTrustedProxyWins() {
		bands.register("alpha", "Alpha");

		ResponseEntity<String> response = api.withHost("internal-api:8080")
			.get("/api/test/tenant", headers -> headers.set(TenantResolutionFilter.FORWARDED_HOST, "alpha.localhost"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).startsWith("alpha ");
	}

}

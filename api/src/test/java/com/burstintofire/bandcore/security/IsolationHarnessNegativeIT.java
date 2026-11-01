package com.burstintofire.bandcore.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.web.CentralEndpoint;
import com.burstintofire.bandcore.shared.web.EndpointRegistry;
import com.burstintofire.bandcore.support.IntegrationTest;

/**
 * Proves the harness catches the mistake it exists for: a band endpoint added without isolation
 * scenarios or an authorization matrix entry makes the build fail.
 */
@Import(IsolationHarnessNegativeIT.Endpoints.class)
class IsolationHarnessNegativeIT extends IntegrationTest {

	@Autowired
	EndpointRegistry endpoints;

	@Test
	void unregisteredBandEndpointIsReported() {
		assertThat(IsolationHarness.unregistered(endpoints.applicationEndpoints(), IsolationScenarios.PROBES))
			.containsExactly("GET /test/forgotten");
	}

	@TestConfiguration(proxyBeanMethods = false)
	@Import({ ForgottenController.class, CentralController.class })
	static class Endpoints {

	}

	/** A band endpoint someone forgot to register. */
	@RestController
	static class ForgottenController {

		@GetMapping("/test/forgotten")
		String forgotten() {
			return "oops";
		}

	}

	/** Central endpoints are exempt: there is no band behind them. */
	@RestController
	@CentralEndpoint
	static class CentralController {

		@GetMapping("/test/central-only")
		String central() {
			return "ok";
		}

	}

}

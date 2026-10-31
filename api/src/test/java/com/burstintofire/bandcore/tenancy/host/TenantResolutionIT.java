package com.burstintofire.bandcore.tenancy.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.AnyHostEndpoint;
import com.burstintofire.bandcore.shared.web.CentralEndpoint;
import com.burstintofire.bandcore.shared.web.PublicEndpoint;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.tenancy.band.Band;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;
import com.burstintofire.bandcore.tenancy.band.BandStatus;

@Import(TenantResolutionIT.TestEndpoints.class)
class TenantResolutionIT extends IntegrationTest {

	@Autowired
	BandRegistry bands;

	Band alpha;

	@BeforeEach
	void createBands() {
		alpha = bands.register("alpha", "Alpha");
		bands.register("beta", "Beta");
	}

	@Test
	void bandHostResolvesItsBand() {
		ResponseEntity<String> response = api.forBand("alpha").get("/api/test/tenant");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("alpha " + alpha.getId());
	}

	@Test
	void unknownBandHostIsNotFound() {
		assertThat(api.forBand("unknown").get("/api/test/tenant").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void malformedAndForeignHostsAreNotFound() {
		for (String host : new String[] { "a.alpha.localhost", "alpha.example.com", "localhost",
				"api.localhost" }) {
			assertThat(api.withHost(host).get("/api/test/tenant").getStatusCode()).as(host)
				.isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	@Test
	void hostIsNormalizedBeforeMatching() {
		for (String host : new String[] { "ALPHA.LOCALHOST", "alpha.localhost:3000", "alpha.localhost." }) {
			ResponseEntity<String> response = api.withHost(host).get("/api/test/tenant");

			assertThat(response.getStatusCode()).as(host).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).as(host).startsWith("alpha ");
		}
	}

	@Test
	void forwardedHostFromAnUntrustedSourceIsIgnored() {
		ResponseEntity<String> response = api.forBand("alpha")
			.get("/api/test/tenant", headers -> headers.set(TenantResolutionFilter.FORWARDED_HOST, "beta.localhost"));

		assertThat(response.getBody()).startsWith("alpha ");
	}

	@Test
	void tenantNeverComesFromTheQueryString() {
		ResponseEntity<String> response = api.forBand("alpha").get("/api/test/tenant?band=beta&band_id=beta");

		assertThat(response.getBody()).startsWith("alpha ");
	}

	@Test
	void suspendedBandIsForbiddenWithAGenericProblem() {
		bands.changeStatus(alpha.getId(), BandStatus.SUSPENDED);

		ResponseEntity<String> response = api.forBand("alpha").get("/api/test/tenant");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).contains("\"code\":\"ACCESS_DENIED\"").doesNotContain("uspend");

		bands.changeStatus(alpha.getId(), BandStatus.ACTIVE);
		assertThat(api.forBand("alpha").get("/api/test/tenant").getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void centralOnlyEndpointIsNotFoundOnABandHost() {
		assertThat(api.central().get("/api/test/central").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(api.forBand("alpha").get("/api/test/central").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void bandEndpointIsNotFoundOnTheCentralHost() {
		assertThat(api.central().get("/api/test/tenant").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void anyHostEndpointSeesTheCentralHostOrTheBand() {
		assertThat(api.central().get("/api/test/any-host").getBody()).isEqualTo("app.localhost central");
		assertThat(api.forBand("alpha").get("/api/test/any-host").getBody()).isEqualTo("alpha.localhost alpha");
	}

	@TestConfiguration(proxyBeanMethods = false)
	@Import(TestController.class)
	static class TestEndpoints {

	}

	@RestController
	@PublicEndpoint("test endpoint")
	static class TestController {

		@GetMapping("/test/tenant")
		String tenant() {
			var tenant = TenantContextHolder.require();
			return tenant.slug() + " " + tenant.bandId();
		}

		@CentralEndpoint
		@GetMapping("/test/central")
		String central() {
			return "central";
		}

		@AnyHostEndpoint
		@GetMapping("/test/any-host")
		String anyHost() {
			var scope = TenantContextHolder.scope().orElseThrow();
			return scope.host() + " " + (scope.isCentral() ? "central" : scope.tenant().slug());
		}

	}

}

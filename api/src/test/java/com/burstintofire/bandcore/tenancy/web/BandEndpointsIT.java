package com.burstintofire.bandcore.tenancy.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;

class BandEndpointsIT extends IntegrationTest {

	UUID alpha;

	UUID ana;

	ApiClient host;

	String token;

	@BeforeEach
	void adminOfAlpha() {
		alpha = fixtures.band("alpha", "Alpha Band");
		ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		host = api.forBand("alpha");
		token = fixtures.login(host, "ana@example.com");
	}

	@Test
	void bandDetailsOfTheCurrentHost() {
		ResponseEntity<String> response = host.get("/api/band", headers -> headers.setBearerAuth(token));

		assertThat(JsonPath.<String>read(response.getBody(), "$.id")).isEqualTo(alpha.toString());
		assertThat(JsonPath.<String>read(response.getBody(), "$.name")).isEqualTo("Alpha Band");
		assertThat(JsonPath.<String>read(response.getBody(), "$.currency")).isEqualTo("BRL");
		assertThat(JsonPath.<String>read(response.getBody(), "$.timezone")).isEqualTo("America/Sao_Paulo");
	}

	@Test
	void adminChangesNameAndTimezone() {
		ResponseEntity<String> response = host.patchJson("/api/band",
				"{\"name\": \" Alpha Renamed \", \"timezone\": \"America/Manaus\"}", headers -> headers.setBearerAuth(token));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(response.getBody(), "$.name")).isEqualTo("Alpha Renamed");
		assertThat(JsonPath.<String>read(response.getBody(), "$.timezone")).isEqualTo("America/Manaus");
		assertThat(JsonPath.<String>read(host.get("/api/band/public-profile").getBody(), "$.name"))
			.isEqualTo("Alpha Renamed");
	}

	@Test
	void invalidTimezoneAndBlankNameAreRejected() {
		ResponseEntity<String> timezone = host.patchJson("/api/band", "{\"timezone\": \"Mars/Olympus\"}",
				headers -> headers.setBearerAuth(token));
		ResponseEntity<String> name = host.patchJson("/api/band", "{\"name\": \"   \"}",
				headers -> headers.setBearerAuth(token));

		assertThat(timezone.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(JsonPath.<String>read(timezone.getBody(), "$.code")).isEqualTo("TIMEZONE_INVALID");
		assertThat(name.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<String>read(name.getBody(), "$.code")).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void publicProfileShowsOnlyTheNameAndSlugWithoutLogin() {
		ResponseEntity<String> response = host.get("/api/band/public-profile");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(response.getBody(), "$.name")).isEqualTo("Alpha Band");
		assertThat(response.getBody()).doesNotContain(alpha.toString()).doesNotContain("timezone");
		assertThat(api.forBand("unknown").get("/api/band/public-profile").getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(api.central().get("/api/band/public-profile").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void membersOfTheBandSortedByName() {
		UUID bia = fixtures.member(alpha, "Bia");

		ResponseEntity<String> list = host.get("/api/members", headers -> headers.setBearerAuth(token));
		ResponseEntity<String> one = host.get("/api/members/" + bia, headers -> headers.setBearerAuth(token));

		assertThat(JsonPath.<List<String>>read(list.getBody(), "$[*].displayName")).containsExactly("Ana", "Bia");
		assertThat(JsonPath.<List<Boolean>>read(list.getBody(), "$[*].linkedToAccount")).containsExactly(true, false);
		assertThat(JsonPath.<String>read(one.getBody(), "$.displayName")).isEqualTo("Bia");
		assertThat(host.get("/api/members/" + UUID.randomUUID(), headers -> headers.setBearerAuth(token))
			.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}

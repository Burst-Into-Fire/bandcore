package com.burstintofire.bandcore.tenancy.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.Cookies;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.TestFixtures;

class MembershipAdministrationIT extends IntegrationTest {

	UUID alpha;

	UUID anaMembership;

	UUID biaMembership;

	ApiClient band;

	String ana;

	@BeforeEach
	void adminAndMember() {
		alpha = fixtures.band("alpha");
		anaMembership = fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		biaMembership = fixtures.membership(alpha, fixtures.verifiedUser("bia@example.com"), "MEMBER");
		band = api.forBand("alpha");
		ana = fixtures.login(band, "ana@example.com");
	}

	@Test
	void adminsSeeWhoHasAccessWithRoleAndLinkedMember() {
		String list = band.get("/api/memberships", headers -> headers.setBearerAuth(ana)).getBody();

		assertThat(JsonPath.<List<String>>read(list, "$[*].displayName")).containsExactly("Ana", "Bia");
		assertThat(JsonPath.<List<String>>read(list, "$[*].email")).containsExactly("ana@example.com", "bia@example.com");
		assertThat(JsonPath.<List<String>>read(list, "$[*].role")).containsExactly("ADMIN", "MEMBER");
		assertThat(JsonPath.<List<String>>read(list, "$[*].memberId")).hasSize(2).doesNotContainNull();
	}

	@Test
	void aRoleChangeTakesEffectOnTheNextRequest() {
		String bia = fixtures.login(band, "bia@example.com");

		ResponseEntity<String> changed = changeRole(biaMembership, "READ_ONLY");

		assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(JsonPath.<String>read(me(bia).getBody(), "$.band.role")).isEqualTo("READ_ONLY");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'membership.role_changed'"))
			.isEqualTo("{\"role\": {\"to\": \"READ_ONLY\", \"from\": \"MEMBER\"}}");
	}

	@Test
	void aRemovedUserLosesAccessAtOnceButStaysAMember() {
		ResponseEntity<String> login = band.postJson("/api/auth/login",
				"{\"email\": \"bia@example.com\", \"password\": \"%s\"}".formatted(TestFixtures.PASSWORD));
		String bia = JsonPath.read(login.getBody(), "$.accessToken");

		ResponseEntity<String> removed = band.send(HttpMethod.DELETE, "/api/memberships/" + biaMembership, null,
				headers -> headers.setBearerAuth(ana));

		assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(me(bia).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		ResponseEntity<String> refresh = band.postJson("/api/auth/refresh", null,
				headers -> Cookies.send(headers, Cookies.refreshToken(login)));
		assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(band
			.postJson("/api/auth/login",
					"{\"email\": \"bia@example.com\", \"password\": \"%s\"}".formatted(TestFixtures.PASSWORD))
			.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(JsonPath.<List<String>>read(band.get("/api/members", headers -> headers.setBearerAuth(ana)).getBody(),
				"$[*].displayName"))
			.containsExactly("Ana", "Bia");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT action FROM bandcore.audit_events WHERE action LIKE 'membership.%'"))
			.isEqualTo("membership.removed");
	}

	@Test
	void theLastAdminCannotBeDemotedRemovedOrLeave() {
		assertProblem(changeRole(anaMembership, "MEMBER"), HttpStatus.CONFLICT, "LAST_ADMIN");
		assertProblem(band.send(HttpMethod.DELETE, "/api/memberships/" + anaMembership, null,
				headers -> headers.setBearerAuth(ana)), HttpStatus.CONFLICT, "LAST_ADMIN");
		assertProblem(band.postJson("/api/memberships/leave", null, headers -> headers.setBearerAuth(ana)),
				HttpStatus.CONFLICT, "LAST_ADMIN");

		changeRole(biaMembership, "ADMIN");
		assertThat(changeRole(anaMembership, "MEMBER").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void twoAdminsDemotingEachOtherAtTheSameTimeLeaveOneAdmin() throws Exception {
		changeRole(biaMembership, "ADMIN");
		String bia = fixtures.login(band, "bia@example.com");
		CountDownLatch start = new CountDownLatch(1);

		CompletableFuture<ResponseEntity<String>> anaDemotesBia = CompletableFuture.supplyAsync(() -> {
			await(start);
			return band.patchJson("/api/memberships/" + biaMembership, "{\"role\": \"MEMBER\"}",
					headers -> headers.setBearerAuth(ana));
		});
		CompletableFuture<ResponseEntity<String>> biaDemotesAna = CompletableFuture.supplyAsync(() -> {
			await(start);
			return band.patchJson("/api/memberships/" + anaMembership, "{\"role\": \"MEMBER\"}",
					headers -> headers.setBearerAuth(bia));
		});
		start.countDown();

		List<Integer> statuses = List.of(anaDemotesBia.get().getStatusCode().value(),
				biaDemotesAna.get().getStatusCode().value());
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT count(*) FROM bandcore.memberships WHERE role = 'ADMIN' AND status = 'ACTIVE'"))
			.isEqualTo("1");
		// The other request either hit the last-admin rule or found its caller no longer an admin.
		assertThat(statuses).contains(204).containsAnyOf(409, 403);
	}

	@Test
	void anyoneCanLeaveAndLosesAccess() {
		String bia = fixtures.login(band, "bia@example.com");

		ResponseEntity<String> left = band.postJson("/api/memberships/leave", null,
				headers -> headers.setBearerAuth(bia));

		assertThat(left.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(me(bia).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT action FROM bandcore.audit_events WHERE action LIKE 'membership.%'"))
			.isEqualTo("membership.left");
	}

	@Test
	void anInvalidRoleIsRejected() {
		assertProblem(band.patchJson("/api/memberships/" + biaMembership, "{\"role\": \"OWNER\"}",
				headers -> headers.setBearerAuth(ana)), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
		assertProblem(band.patchJson("/api/memberships/" + biaMembership, "{}", headers -> headers.setBearerAuth(ana)),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
	}

	private ResponseEntity<String> changeRole(UUID membership, String role) {
		return band.patchJson("/api/memberships/" + membership, "{\"role\": \"%s\"}".formatted(role),
				headers -> headers.setBearerAuth(ana));
	}

	private ResponseEntity<String> me(String token) {
		return band.get("/api/me", headers -> headers.setBearerAuth(token));
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

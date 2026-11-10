package com.burstintofire.bandcore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import com.burstintofire.bandcore.shared.web.EndpointRegistry;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.tenancy.Role;

/**
 * Generates role × endpoint cases from {@link AuthorizationMatrix} and proves each one against
 * the running API. An authenticated endpoint without an entry fails {@link #everyAuthenticatedEndpointIsInTheMatrix}.
 */
class AuthorizationMatrixIT extends IntegrationTest {

	@Autowired
	EndpointRegistry endpoints;

	static Stream<Arguments> bandCases() {
		List<Arguments> cases = new ArrayList<>();
		for (AuthorizationMatrix.Rule rule : AuthorizationMatrix.BAND) {
			for (Role role : Role.values()) {
				cases.add(Arguments.of(rule.key(), role, rule));
			}
		}
		return cases.stream();
	}

	@ParameterizedTest(name = "{0} as {1}")
	@MethodSource("bandCases")
	void bandEndpointsFollowTheMatrix(String endpoint, Role role, AuthorizationMatrix.Rule rule) {
		UUID band = fixtures.band("alpha");
		UUID user = fixtures.verifiedUser("user@example.com");
		fixtures.membership(band, user, role.name());
		UUID member = fixtures.member(band, "Bia");
		// An account with access to the band and no member record, for linking.
		UUID unlinked = fixtures.verifiedUser("unlinked@example.com");
		fixtures.membershipOnly(band, unlinked, "MEMBER");
		UUID invitation = fixtures.invitation(band, "invited@example.com", "MEMBER", user).id();
		// A second admin, so an admin under test may leave, and whose role and access are changed.
		UUID otherAdmin = fixtures.membership(band, fixtures.verifiedUser("other.admin@example.com"), "ADMIN");
		ApiClient host = api.forBand("alpha");
		String token = fixtures.login(host, "user@example.com");
		UUID category = fixtures.category(band, "Gear");
		// The expense uses another category, so the category under test can be deleted.
		UUID travel = fixtures.category(band, "Travel");
		UUID expense = fixtures.plannedExpense(band, travel, "Ensaio", UUID.randomUUID());
		UUID paidExpense = fixtures.paidExpense(band, travel, "Van", UUID.randomUUID());
		// Two members without accounts settle between themselves: no role under test is a party.
		UUID payer = fixtures.member(band, "Caio");
		UUID payee = fixtures.member(band, "Dani");
		UUID settlement = fixtures.settlement(band, payer, payee, "Pix", UUID.randomUUID());
		UUID product = fixtures.product(band, "Camiseta");
		UUID variant = fixtures.variant(band, product, "M");
		fixtures.variant(band, product, "G");
		// The batch is on another product, so the one under test has no history and may be deleted.
		UUID batch = fixtures.batch(band, fixtures.variant(band, fixtures.product(band, "Patch"), "U"), 10, 500,
				"Gráfica");
		UUID event = fixtures.event(band, "Festival", java.time.Instant.parse("2026-02-01T20:00:00Z"));
		UUID saleProduct = fixtures.product(band, "Sale probe");
		UUID saleVariant = fixtures.variant(band, saleProduct, "U");
		fixtures.batch(band, saleVariant, 2, 500, "Sale probe supplier");
		var fixture = new AuthorizationMatrix.Fixture(Map.ofEntries(Map.entry("memberId", member.toString()),
				Map.entry("userId", unlinked.toString()), Map.entry("invitationId", invitation.toString()),
				Map.entry("membershipId", otherAdmin.toString()), Map.entry("categoryId", category.toString()),
				Map.entry("expenseId", expense.toString()), Map.entry("paidExpenseId", paidExpense.toString()),
				Map.entry("settlementId", settlement.toString()), Map.entry("payerId", payer.toString()),
				Map.entry("payeeId", payee.toString()), Map.entry("productId", product.toString()),
				Map.entry("variantId", variant.toString()), Map.entry("batchId", batch.toString()),
				Map.entry("eventId", event.toString()), Map.entry("saleVariantId", saleVariant.toString())));

		ResponseEntity<String> response = host.send(rule.method(), rule.url(fixture), rule.body().apply(fixture),
				headers -> headers.setBearerAuth(token));

		if (rule.allowed().contains(role)) {
			assertThat(response.getStatusCode().is2xxSuccessful()).as("%s allowed for %s: %s", endpoint, role, response)
				.isTrue();
		}
		else {
			assertThat(response.getStatusCode()).as("%s denied for %s", endpoint, role).isEqualTo(HttpStatus.FORBIDDEN);
			assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("ACCESS_DENIED");
		}
	}

	static Stream<AuthorizationMatrix.Rule> centralCases() {
		return AuthorizationMatrix.CENTRAL.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("centralCases")
	void centralEndpointsNeedAnAccountButNoRole(AuthorizationMatrix.Rule rule) {
		UUID user = fixtures.verifiedUser("user@example.com");
		// An invitation for this account, for the acceptance endpoint.
		UUID band = fixtures.band("inviting");
		UUID inviter = fixtures.verifiedUser("inviter@example.com");
		fixtures.membership(band, inviter, "ADMIN");
		String invitationToken = fixtures.invitation(band, "user@example.com", "MEMBER", inviter).token();
		String token = fixtures.login(api.central(), "user@example.com");
		var fixture = new AuthorizationMatrix.Fixture(Map.of("invitationToken", invitationToken));

		ResponseEntity<String> anonymous = api.central().send(rule.method(), rule.url(fixture), rule.body().apply(fixture),
				headers -> {
				});
		ResponseEntity<String> authenticated = api.central().send(rule.method(), rule.url(fixture),
				rule.body().apply(fixture), headers -> headers.setBearerAuth(token));

		assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(authenticated.getStatusCode().is2xxSuccessful()).as("%s: %s", rule.key(), authenticated).isTrue();
	}

	@Test
	void readOnlyCannotPerformAnyWrite() {
		assertThat(AuthorizationMatrix.BAND).filteredOn(AuthorizationMatrix.Rule::isWrite)
			// Self-service writes about one's own session or access, open to every role.
			.filteredOn(rule -> !rule.path().startsWith("/auth/") && !rule.path().equals("/memberships/leave"))
			.allSatisfy(rule -> assertThat(rule.allowed()).doesNotContain(Role.READ_ONLY));
	}

	@Test
	void everyAuthenticatedEndpointIsInTheMatrix() {
		Set<String> inMatrix = new TreeSet<>();
		AuthorizationMatrix.BAND.forEach(rule -> inMatrix.add(rule.key()));
		AuthorizationMatrix.CENTRAL.forEach(rule -> inMatrix.add(rule.key()));

		Set<String> missing = new TreeSet<>();
		endpoints.applicationEndpoints().forEach((info, handler) -> {
			if (!EndpointRegistry.isPublic(handler)) {
				keys(info, handler).stream().filter(key -> !inMatrix.contains(key)).forEach(missing::add);
			}
		});

		assertThat(missing).as("authenticated endpoints missing from AuthorizationMatrix").isEmpty();
	}

	static List<String> keys(org.springframework.web.servlet.mvc.method.RequestMappingInfo info, HandlerMethod handler) {
		List<String> keys = new ArrayList<>();
		for (var method : info.getMethodsCondition().getMethods()) {
			for (String pattern : info.getPatternValues()) {
				keys.add(method.name() + " " + pattern);
			}
		}
		if (keys.isEmpty()) {
			keys.add("ANY " + info.getPatternValues() + " " + handler);
		}
		return keys;
	}

}

package com.burstintofire.bandcore.security;

import static com.burstintofire.bandcore.tenancy.Role.ADMIN;
import static com.burstintofire.bandcore.tenancy.Role.MEMBER;
import static com.burstintofire.bandcore.tenancy.Role.READ_ONLY;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpMethod;

import com.burstintofire.bandcore.tenancy.Role;

/**
 * The authorization matrix as data: for every authenticated endpoint, which roles may call it.
 * {@code AuthorizationMatrixIT} turns each entry into one test per role, and fails when an
 * authenticated endpoint has no entry. Keep it in sync with docs/security/permissions.md.
 *
 * <p>Adding an endpoint: add a {@link Rule} with the path template exactly as mapped (relative to
 * {@code /api}), the roles allowed, and a request body for writes. Path variables are filled from
 * {@link Fixture#pathVariables()}. Central endpoints (no band, no role) go in {@link #CENTRAL}.
 */
final class AuthorizationMatrix {

	static final Set<Role> EVERY_ROLE = EnumSet.allOf(Role.class);

	static final Set<Role> ADMIN_ONLY = EnumSet.of(ADMIN);

	/** Roles that create expenses and change their own (finance:write). */
	static final Set<Role> WRITERS = EnumSet.of(ADMIN, MEMBER);

	/** Endpoints on band hosts (and any-host endpoints, checked on a band host). */
	static final List<Rule> BAND = List.of(
			rule(HttpMethod.GET, "/me", EVERY_ROLE),
			rule(HttpMethod.POST, "/auth/logout-all", EVERY_ROLE),
			rule(HttpMethod.POST, "/auth/handoff", EVERY_ROLE, fixture -> "{}"),
			rule(HttpMethod.GET, "/band", EVERY_ROLE),
			rule(HttpMethod.PATCH, "/band", EnumSet.of(ADMIN), fixture -> "{\"name\": \"Renamed\"}"),
			rule(HttpMethod.GET, "/members", EVERY_ROLE),
			rule(HttpMethod.GET, "/members/{memberId}", EVERY_ROLE),
			rule(HttpMethod.POST, "/members", ADMIN_ONLY, fixture -> "{\"displayName\": \"New member\"}"),
			rule(HttpMethod.PATCH, "/members/{memberId}", ADMIN_ONLY, fixture -> "{\"instrument\": \"Baixo\"}"),
			rule(HttpMethod.POST, "/members/{memberId}/deactivate", ADMIN_ONLY),
			rule(HttpMethod.POST, "/members/{memberId}/activate", ADMIN_ONLY),
			rule(HttpMethod.DELETE, "/members/{memberId}", ADMIN_ONLY),
			rule(HttpMethod.PUT, "/members/{memberId}/account", ADMIN_ONLY,
					fixture -> "{\"userId\": \"%s\"}".formatted(fixture.pathVariables().get("userId"))),
			rule(HttpMethod.DELETE, "/members/{memberId}/account", ADMIN_ONLY),
			rule(HttpMethod.GET, "/invitations", ADMIN_ONLY),
			rule(HttpMethod.POST, "/invitations", ADMIN_ONLY,
					fixture -> "{\"email\": \"new@example.com\", \"role\": \"MEMBER\"}"),
			rule(HttpMethod.POST, "/invitations/{invitationId}/resend", ADMIN_ONLY),
			rule(HttpMethod.DELETE, "/invitations/{invitationId}", ADMIN_ONLY),
			rule(HttpMethod.GET, "/memberships", ADMIN_ONLY),
			rule(HttpMethod.PATCH, "/memberships/{membershipId}", ADMIN_ONLY, fixture -> "{\"role\": \"MEMBER\"}"),
			rule(HttpMethod.DELETE, "/memberships/{membershipId}", ADMIN_ONLY),
			rule(HttpMethod.POST, "/memberships/leave", EVERY_ROLE),
			rule(HttpMethod.GET, "/expense-categories", EVERY_ROLE),
			rule(HttpMethod.POST, "/expense-categories", ADMIN_ONLY, fixture -> "{\"name\": \"New category\"}"),
			rule(HttpMethod.PATCH, "/expense-categories/{categoryId}", ADMIN_ONLY,
					fixture -> "{\"color\": \"#ff6b5a\"}"),
			rule(HttpMethod.POST, "/expense-categories/{categoryId}/archive", ADMIN_ONLY),
			rule(HttpMethod.POST, "/expense-categories/{categoryId}/unarchive", ADMIN_ONLY),
			rule(HttpMethod.DELETE, "/expense-categories/{categoryId}", ADMIN_ONLY),
			rule(HttpMethod.GET, "/expenses", EVERY_ROLE),
			rule(HttpMethod.GET, "/expenses/{expenseId}", EVERY_ROLE),
			rule(HttpMethod.POST, "/expenses", WRITERS, fixture -> """
					{"description": "Ensaio", "categoryId": "%s", "currency": "BRL", "amountCents": 5000,
					 "expenseDate": "2026-11-10", "status": "PLANNED"}""".formatted(fixture.pathVariables().get("categoryId"))),
			// The fixture expense was created by someone else: members may only change their own.
			rule(HttpMethod.PATCH, "/expenses/{expenseId}", ADMIN_ONLY,
					fixture -> "{\"version\": 0, \"description\": \"Renamed\"}"),
			rule(HttpMethod.DELETE, "/expenses/{expenseId}", ADMIN_ONLY),
			rule(HttpMethod.GET, "/expenses/upcoming", EVERY_ROLE),
			// Members may mark anyone's planned expense as paid by themselves (no payer: their own member).
			rule(HttpMethod.POST, "/expenses/{expenseId}/pay", WRITERS, fixture -> "{}"),
			rule(HttpMethod.POST, "/expenses/{expenseId}/revert-payment", ADMIN_ONLY, fixture -> "{}")
				.with("expenseId", "paidExpenseId"),
			rule(HttpMethod.GET, "/balances", EVERY_ROLE),
			rule(HttpMethod.GET, "/balances/settlement-plan", EVERY_ROLE),
			rule(HttpMethod.GET, "/summary/finance", EVERY_ROLE),
			rule(HttpMethod.GET, "/settlements", EVERY_ROLE),
			rule(HttpMethod.GET, "/settlements/{settlementId}", EVERY_ROLE),
			rule(HttpMethod.POST, "/settlements", WRITERS, fixture -> """
					{"fromMemberId": "%s", "toMemberId": "%s", "currency": "BRL", "amountCents": 1000}"""
				.formatted(fixture.pathVariables().get("payerId"), fixture.pathVariables().get("payeeId"))),
			// The fixture settlement is between two other members and recorded by someone else.
			rule(HttpMethod.PATCH, "/settlements/{settlementId}", ADMIN_ONLY,
					fixture -> "{\"version\": 0, \"note\": \"Pix\"}"),
			rule(HttpMethod.DELETE, "/settlements/{settlementId}", ADMIN_ONLY),
			rule(HttpMethod.GET, "/products", EVERY_ROLE),
			rule(HttpMethod.GET, "/products/{productId}", EVERY_ROLE),
			rule(HttpMethod.POST, "/products", ADMIN_ONLY,
					fixture -> "{\"name\": \"New product\", \"currency\": \"BRL\", \"defaultPriceCents\": 5000}"),
			rule(HttpMethod.PATCH, "/products/{productId}", ADMIN_ONLY, fixture -> "{\"version\": 0, \"name\": \"Renamed\"}"),
			rule(HttpMethod.POST, "/products/{productId}/archive", ADMIN_ONLY),
			rule(HttpMethod.POST, "/products/{productId}/unarchive", ADMIN_ONLY),
			rule(HttpMethod.DELETE, "/products/{productId}", ADMIN_ONLY),
			rule(HttpMethod.POST, "/products/{productId}/variants", ADMIN_ONLY, fixture -> "{\"size\": \"XG\"}"),
			rule(HttpMethod.PUT, "/variants/{variantId}", ADMIN_ONLY, fixture -> "{\"version\": 0, \"size\": \"GG\"}"),
			rule(HttpMethod.POST, "/variants/{variantId}/archive", ADMIN_ONLY),
			rule(HttpMethod.POST, "/variants/{variantId}/unarchive", ADMIN_ONLY),
			// The fixture product has two variants, so the one under test may be deleted.
			rule(HttpMethod.DELETE, "/variants/{variantId}", ADMIN_ONLY),
			rule(HttpMethod.GET, "/variants/{variantId}/movements", EVERY_ROLE));

	/** Endpoints on the central host: any authenticated account, no role. */
	static final List<Rule> CENTRAL = List.of(
			rule(HttpMethod.GET, "/me/bands", EVERY_ROLE),
			rule(HttpMethod.GET, "/bands/slug-availability", EVERY_ROLE, fixture -> null, "?slug=free-slug"),
			rule(HttpMethod.POST, "/bands", EVERY_ROLE, fixture -> "{\"name\": \"New\", \"slug\": \"brand-new\"}"),
			rule(HttpMethod.POST, "/invitations/accept", EVERY_ROLE,
					fixture -> "{\"token\": \"%s\"}".formatted(fixture.pathVariables().get("invitationToken"))));

	/** What a test case knows about the band it runs in. */
	record Fixture(Map<String, String> pathVariables) {
	}

	/**
	 * @param resources which fixture resource fills a path variable when it is not the one with the
	 *        same name (for example a paid expense for {@code {expenseId}})
	 */
	record Rule(HttpMethod method, String path, Set<Role> allowed, Function<Fixture, String> body, String query,
			Map<String, String> resources) {

		/** The same rule, filling {@code variable} with the fixture's {@code resource}. */
		Rule with(String variable, String resource) {
			Map<String, String> merged = new java.util.HashMap<>(resources);
			merged.put(variable, resource);
			return new Rule(method, path, allowed, body, query, Map.copyOf(merged));
		}


		String key() {
			return method + " " + path;
		}

		String url(Fixture fixture) {
			String url = path;
			for (Map.Entry<String, String> alias : resources.entrySet()) {
				url = url.replace("{" + alias.getKey() + "}", fixture.pathVariables().get(alias.getValue()));
			}
			for (Map.Entry<String, String> variable : fixture.pathVariables().entrySet()) {
				url = url.replace("{" + variable.getKey() + "}", variable.getValue());
			}
			return "/api" + url + query;
		}

		boolean isWrite() {
			return !method.equals(HttpMethod.GET);
		}

	}

	private static Rule rule(HttpMethod method, String path, Set<Role> allowed) {
		return rule(method, path, allowed, fixture -> null);
	}

	private static Rule rule(HttpMethod method, String path, Set<Role> allowed, Function<Fixture, String> body) {
		return rule(method, path, allowed, body, "");
	}

	private static Rule rule(HttpMethod method, String path, Set<Role> allowed, Function<Fixture, String> body,
			String query) {
		return new Rule(method, path, allowed, body, query, Map.of());
	}

	private AuthorizationMatrix() {
	}

}

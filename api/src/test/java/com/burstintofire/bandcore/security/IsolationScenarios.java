package com.burstintofire.bandcore.security;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.HttpMethod;

/**
 * Every band endpoint, described so the isolation harness can aim it at another band's data.
 * {@code TenantIsolationIT} runs six attacks against each probe and fails when an endpoint is
 * neither listed here nor {@code @PublicEndpoint}/{@code @CentralEndpoint}/{@code @PlatformEndpoint}.
 *
 * <p>Registering an endpoint: add a {@link Probe} with the path template exactly as mapped
 * (relative to {@code /api}). Path variables are filled with the target band's resources from
 * {@link Target#resources()}; add a resource there when an endpoint needs a new kind of id.
 * Writes need a body; the harness adds the target band's ids to every query and body anyway.
 * See docs/endpoint-security.md.
 */
final class IsolationScenarios {

	static final List<Probe> PROBES = List.of(
			probe(HttpMethod.GET, "/me"),
			probe(HttpMethod.POST, "/auth/logout-all"),
			probe(HttpMethod.POST, "/auth/handoff", target -> "{\"slug\": \"%s\"}".formatted(target.slug())),
			probe(HttpMethod.GET, "/band"),
			probe(HttpMethod.PATCH, "/band", target -> "{\"name\": \"Taken over\"}"),
			probe(HttpMethod.GET, "/members"),
			probe(HttpMethod.GET, "/members/{memberId}"),
			probe(HttpMethod.POST, "/members", target -> "{\"displayName\": \"Intruder\"}"),
			probe(HttpMethod.PATCH, "/members/{memberId}", target -> "{\"displayName\": \"Taken over\"}"),
			probe(HttpMethod.POST, "/members/{memberId}/deactivate"),
			probe(HttpMethod.POST, "/members/{memberId}/activate"),
			probe(HttpMethod.DELETE, "/members/{memberId}"),
			probe(HttpMethod.PUT, "/members/{memberId}/account",
					target -> "{\"userId\": \"%s\"}".formatted(target.resources().get("userId"))),
			probe(HttpMethod.DELETE, "/members/{memberId}/account"),
			probe(HttpMethod.GET, "/invitations"),
			probe(HttpMethod.POST, "/invitations",
					target -> "{\"email\": \"intruder@example.com\", \"role\": \"ADMIN\", \"memberId\": \"%s\"}"
						.formatted(target.resources().get("memberId"))),
			probe(HttpMethod.POST, "/invitations/{invitationId}/resend"),
			probe(HttpMethod.DELETE, "/invitations/{invitationId}"),
			probe(HttpMethod.GET, "/memberships"),
			probe(HttpMethod.PATCH, "/memberships/{membershipId}", target -> "{\"role\": \"READ_ONLY\"}"),
			probe(HttpMethod.DELETE, "/memberships/{membershipId}"),
			probe(HttpMethod.POST, "/memberships/leave"),
			probe(HttpMethod.GET, "/expense-categories"),
			probe(HttpMethod.POST, "/expense-categories", target -> "{\"name\": \"Intruder category\"}"),
			probe(HttpMethod.PATCH, "/expense-categories/{categoryId}", target -> "{\"name\": \"Taken over\"}"),
			probe(HttpMethod.POST, "/expense-categories/{categoryId}/archive"),
			probe(HttpMethod.POST, "/expense-categories/{categoryId}/unarchive"),
			probe(HttpMethod.DELETE, "/expense-categories/{categoryId}"),
			probe(HttpMethod.GET, "/expenses"),
			probe(HttpMethod.GET, "/expenses/{expenseId}"),
			probe(HttpMethod.POST, "/expenses", target -> """
					{"description": "Intruder", "categoryId": "%s", "currency": "BRL", "amountCents": 100,
					 "expenseDate": "2026-11-10", "status": "PAID", "paidByMemberId": "%s"}"""
				.formatted(target.resources().get("categoryId"), target.resources().get("memberId"))),
			probe(HttpMethod.PATCH, "/expenses/{expenseId}",
					target -> "{\"version\": 0, \"description\": \"Taken over\"}"),
			probe(HttpMethod.DELETE, "/expenses/{expenseId}"),
			probe(HttpMethod.GET, "/expenses/upcoming"),
			probe(HttpMethod.POST, "/expenses/{expenseId}/pay",
					target -> "{\"payerMemberId\": \"%s\"}".formatted(target.resources().get("memberId"))),
			probe(HttpMethod.POST, "/expenses/{expenseId}/revert-payment", target -> "{\"version\": 0}"),
			probe(HttpMethod.GET, "/balances"),
			probe(HttpMethod.GET, "/balances/settlement-plan"),
			probe(HttpMethod.GET, "/summary/finance"),
			probe(HttpMethod.GET, "/settlements"),
			probe(HttpMethod.GET, "/settlements/{settlementId}"),
			probe(HttpMethod.POST, "/settlements", target -> """
					{"fromMemberId": "%s", "toMemberId": "%s", "currency": "BRL", "amountCents": 100}"""
				.formatted(target.resources().get("memberId"), target.resources().get("otherMemberId"))),
			probe(HttpMethod.PATCH, "/settlements/{settlementId}",
					target -> "{\"version\": 0, \"note\": \"Taken over\"}"),
			probe(HttpMethod.DELETE, "/settlements/{settlementId}"),
			probe(HttpMethod.GET, "/products"),
			probe(HttpMethod.GET, "/products/{productId}"),
			probe(HttpMethod.POST, "/products",
					target -> "{\"name\": \"Intruder product\", \"currency\": \"BRL\", \"defaultPriceCents\": 100}"),
			probe(HttpMethod.PATCH, "/products/{productId}", target -> "{\"version\": 0, \"name\": \"Taken over\"}"),
			probe(HttpMethod.POST, "/products/{productId}/archive"),
			probe(HttpMethod.POST, "/products/{productId}/unarchive"),
			probe(HttpMethod.DELETE, "/products/{productId}"),
			probe(HttpMethod.POST, "/products/{productId}/variants", target -> "{\"size\": \"XG\"}"),
			probe(HttpMethod.PUT, "/variants/{variantId}", target -> "{\"version\": 0, \"size\": \"XG\"}"),
			probe(HttpMethod.POST, "/variants/{variantId}/archive"),
			probe(HttpMethod.POST, "/variants/{variantId}/unarchive"),
			probe(HttpMethod.DELETE, "/variants/{variantId}"),
			probe(HttpMethod.GET, "/variants/{variantId}/movements"),
			probe(HttpMethod.POST, "/variants/{variantId}/adjustments",
					target -> "{\"quantityDelta\": -1, \"reason\": \"LOST\"}"),
			probe(HttpMethod.GET, "/batches"),
			probe(HttpMethod.GET, "/batches/{batchId}"),
			probe(HttpMethod.POST, "/batches", target -> """
					{"currency": "BRL", "costMode": "PER_ITEM", "items": [{"variantId": "%s", "quantity": 1, "unitCostCents": 100}]}"""
				.formatted(target.resources().get("variantId"))),
			probe(HttpMethod.PATCH, "/batches/{batchId}", target -> "{\"version\": 0, \"notes\": \"Taken over\"}"),
			probe(HttpMethod.POST, "/batches/{batchId}/cancel", target -> "{\"version\": 0}"),
			probe(HttpMethod.GET, "/events"),
			probe(HttpMethod.GET, "/events/{eventId}"),
			probe(HttpMethod.POST, "/events", target -> """
					{"name": "Intruder", "startsAt": "2026-02-01T20:00:00Z"}"""),
			probe(HttpMethod.PATCH, "/events/{eventId}", target -> "{\"version\": 0, \"name\": \"Taken over\"}"));

	/** The band an attack aims at, and its resources by path variable name. */
	record Target(String bandId, String slug, Map<String, String> resources) {
	}

	record Probe(HttpMethod method, String path, Function<Target, String> body) {

		String key() {
			return method + " " + path;
		}

		/** Whether the path names a resource, which must then answer 404 for another band. */
		boolean addressesResource() {
			return path.contains("{");
		}

		/**
		 * The URL with the target band's ids in the path, plus the target band's id in the query
		 * (no endpoint may take the band from a parameter).
		 */
		String url(Target target) {
			String url = path;
			for (Map.Entry<String, String> resource : target.resources().entrySet()) {
				url = url.replace("{" + resource.getKey() + "}", resource.getValue());
			}
			return "/api" + url + "?bandId=" + target.bandId() + "&band_id=" + target.bandId() + "&band=" + target.slug();
		}

		/** The probe's body with the target band's ids added, or null for requests without one. */
		String body(Target target) {
			String json = body.apply(target);
			if (json == null) {
				return null;
			}
			String ids = "\"bandId\": \"%s\", \"band_id\": \"%s\", ".formatted(target.bandId(), target.bandId());
			return json.replaceFirst("\\{", "{" + ids);
		}

	}

	private static Probe probe(HttpMethod method, String path) {
		return probe(method, path, target -> method == HttpMethod.GET ? null : "{}");
	}

	private static Probe probe(HttpMethod method, String path, Function<Target, String> body) {
		return new Probe(method, path, body);
	}

	private IsolationScenarios() {
	}

}

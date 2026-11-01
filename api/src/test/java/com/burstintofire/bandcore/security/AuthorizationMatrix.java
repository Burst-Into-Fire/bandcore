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

	/** Endpoints on band hosts (and any-host endpoints, checked on a band host). */
	static final List<Rule> BAND = List.of(
			rule(HttpMethod.GET, "/me", EVERY_ROLE),
			rule(HttpMethod.POST, "/auth/logout-all", EVERY_ROLE),
			rule(HttpMethod.GET, "/band", EVERY_ROLE),
			rule(HttpMethod.PATCH, "/band", EnumSet.of(ADMIN), fixture -> "{\"name\": \"Renamed\"}"),
			rule(HttpMethod.GET, "/members", EVERY_ROLE),
			rule(HttpMethod.GET, "/members/{memberId}", EVERY_ROLE));

	/** Endpoints on the central host: any authenticated account, no role. */
	static final List<Rule> CENTRAL = List.of(
			rule(HttpMethod.GET, "/me/bands", EVERY_ROLE),
			rule(HttpMethod.GET, "/bands/slug-availability", EVERY_ROLE, fixture -> null, "?slug=free-slug"),
			rule(HttpMethod.POST, "/bands", EVERY_ROLE, fixture -> "{\"name\": \"New\", \"slug\": \"brand-new\"}"));

	/** What a test case knows about the band it runs in. */
	record Fixture(Map<String, String> pathVariables) {
	}

	record Rule(HttpMethod method, String path, Set<Role> allowed, Function<Fixture, String> body, String query) {

		String key() {
			return method + " " + path;
		}

		String url(Fixture fixture) {
			String url = path;
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
		return new Rule(method, path, allowed, body, query);
	}

	private AuthorizationMatrix() {
	}

}

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
			probe(HttpMethod.GET, "/band"),
			probe(HttpMethod.PATCH, "/band", target -> "{\"name\": \"Taken over\"}"),
			probe(HttpMethod.GET, "/members"),
			probe(HttpMethod.GET, "/members/{memberId}"));

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

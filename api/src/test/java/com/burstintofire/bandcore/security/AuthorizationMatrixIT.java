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
		ApiClient host = api.forBand("alpha");
		String token = fixtures.login(host, "user@example.com");
		var fixture = new AuthorizationMatrix.Fixture(Map.of("memberId", member.toString()));

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
		fixtures.verifiedUser("user@example.com");
		String token = fixtures.login(api.central(), "user@example.com");
		var fixture = new AuthorizationMatrix.Fixture(Map.of());

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
			.filteredOn(rule -> !rule.path().startsWith("/auth/"))
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

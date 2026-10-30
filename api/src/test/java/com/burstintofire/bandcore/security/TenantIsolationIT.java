package com.burstintofire.bandcore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.burstintofire.bandcore.shared.web.EndpointRegistry;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.TestClockConfiguration;
import com.burstintofire.bandcore.support.TestTokens;

/**
 * Cross-tenant isolation, proven for every band endpoint. Band A ("alpha") attacks band B
 * ("bravo-secret") in six ways; each attack must fail with 401 or 404, the response must contain
 * none of band B's identifiers or names, and band B's data must be unchanged afterwards.
 */
class TenantIsolationIT extends IntegrationTest {

	enum Attack {

		/** 1. Band A's token sent to band B's host. */
		TOKEN_ON_OTHER_HOST,
		/** 2. Band A's token on band A's host, aimed at band B's ids (path, query and body). */
		OTHER_BANDS_IDS,
		/** 3. A user of both bands, with band A's token on band A's host, aimed at band B's ids. */
		MEMBER_OF_BOTH_BANDS,
		/** 4. Band A's token with band_id (and aud) changed to band B, signature unchanged. */
		TAMPERED_BAND_CLAIM,
		/** 5. No token at all, on band B's host. */
		MISSING_TOKEN,
		/** 6. A correctly signed but expired token for band B. */
		EXPIRED_TOKEN

	}

	@Autowired
	EndpointRegistry endpoints;

	@Autowired
	JwtEncoder encoder;

	static Stream<Arguments> attacks() {
		List<Arguments> cases = new ArrayList<>();
		for (IsolationScenarios.Probe probe : IsolationScenarios.PROBES) {
			for (Attack attack : Attack.values()) {
				cases.add(Arguments.of(probe.key(), attack, probe));
			}
		}
		return cases.stream();
	}

	@ParameterizedTest(name = "{0}: {1}")
	@MethodSource("attacks")
	void bandBStaysOutOfReach(String endpoint, Attack attack, IsolationScenarios.Probe probe) {
		World world = World.create(this);
		List<String> before = IsolationHarness.snapshot(world.bravo);

		ResponseEntity<String> response = attack(world, attack, probe);

		assertThat(response.getStatusCode().value()).as("%s: %s", endpoint, attack)
			.isIn(expectedStatuses(attack, probe));
		String body = response.getBody() == null ? "" : response.getBody();
		for (String secret : world.bravoSecrets()) {
			assertThat(body).as("%s: %s leaked band B data", endpoint, attack).doesNotContain(secret);
		}
		assertThat(IsolationHarness.snapshot(world.bravo)).as("%s: %s changed band B", endpoint, attack)
			.isEqualTo(before);
	}

	@Test
	void everyBandEndpointHasIsolationScenarios() {
		assertThat(IsolationHarness.unregistered(endpoints.applicationEndpoints(), IsolationScenarios.PROBES))
			.as("register these endpoints in IsolationScenarios (docs/endpoint-security.md)")
			.isEmpty();
	}

	@Test
	void everyPublicEndpointSaysWhy() {
		assertThat(IsolationHarness.publicWithoutReason(endpoints.applicationEndpoints())).isEmpty();
	}

	@Test
	void everyProbeMatchesAnExistingEndpoint() {
		List<String> existing = new ArrayList<>();
		endpoints.applicationEndpoints().forEach((info, handler) -> existing.addAll(AuthorizationMatrixIT.keys(info, handler)));

		assertThat(existing).containsAll(IsolationScenarios.PROBES.stream().map(IsolationScenarios.Probe::key).toList());
	}

	private ResponseEntity<String> attack(World world, Attack attack, IsolationScenarios.Probe probe) {
		IsolationScenarios.Target bravo = world.bravoTarget();
		IsolationScenarios.Target alpha = world.alphaTarget();
		return switch (attack) {
			case TOKEN_ON_OTHER_HOST -> call(world.bravoHost, probe, bravo, world.alphaAdminToken);
			case OTHER_BANDS_IDS -> call(world.alphaHost, probe, bravo, world.alphaAdminToken);
			case MEMBER_OF_BOTH_BANDS -> call(world.alphaHost, probe, bravo, world.bothBandsAlphaToken);
			case TAMPERED_BAND_CLAIM -> call(world.bravoHost, probe, bravo, TestTokens.withClaim(
					TestTokens.withClaim(world.alphaAdminToken, "band_id", world.bravo.toString()), "aud", "bravo-secret.localhost"));
			case MISSING_TOKEN -> call(world.bravoHost, probe, bravo, null);
			case EXPIRED_TOKEN -> call(world.bravoHost, probe, bravo,
					new TestTokens(encoder).signed(world.bravoAdmin, "bravo-secret.localhost", world.bravo,
							claims -> claims.issuedAt(TestClockConfiguration.NOW.minusSeconds(3600))
								.expiresAt(TestClockConfiguration.NOW.minusSeconds(60))));
		};
	}

	/**
	 * Attacks with band A's own credentials on band A's host may legitimately succeed on band A's
	 * data (or be refused by role); what they must never do is reach band B. Resource endpoints
	 * must answer 404 for band B's ids.
	 */
	private static List<Integer> expectedStatuses(Attack attack, IsolationScenarios.Probe probe) {
		boolean ownHost = attack == Attack.OTHER_BANDS_IDS || attack == Attack.MEMBER_OF_BOTH_BANDS;
		if (!ownHost) {
			return List.of(401);
		}
		if (probe.addressesResource()) {
			return List.of(404);
		}
		return List.of(200, 204, 403);
	}

	private static ResponseEntity<String> call(ApiClient host, IsolationScenarios.Probe probe,
			IsolationScenarios.Target target, String token) {
		return host.send(probe.method(), probe.url(target), probe.body(target), headers -> {
			if (token != null) {
				headers.setBearerAuth(token);
			}
		});
	}

	/** Two bands with users of every role, one user in both bands, and named members. */
	static final class World {

		UUID alpha;

		UUID bravo;

		UUID bravoAdmin;

		UUID alphaMember;

		UUID bravoMember;

		ApiClient alphaHost;

		ApiClient bravoHost;

		String alphaAdminToken;

		String bothBandsAlphaToken;

		static World create(TenantIsolationIT test) {
			var fixtures = test.fixtures;
			World world = new World();
			world.alpha = fixtures.band("alpha", "Alpha Band");
			world.bravo = fixtures.band("bravo-secret", "Bravo Secret Band");

			UUID alphaAdmin = fixtures.verifiedUser("alpha.admin@example.com");
			fixtures.membership(world.alpha, alphaAdmin, "ADMIN");
			fixtures.membership(world.alpha, fixtures.verifiedUser("alpha.member@example.com"), "MEMBER");
			fixtures.membership(world.alpha, fixtures.verifiedUser("alpha.reader@example.com"), "READ_ONLY");
			world.bravoAdmin = fixtures.verifiedUser("bravo.admin@example.com");
			fixtures.membership(world.bravo, world.bravoAdmin, "ADMIN");
			UUID both = fixtures.verifiedUser("both.bands@example.com");
			fixtures.membership(world.alpha, both, "ADMIN");
			fixtures.membership(world.bravo, both, "ADMIN");

			world.alphaMember = fixtures.member(world.alpha, "Alice Alpha");
			world.bravoMember = fixtures.member(world.bravo, "Bruno Bravo Secreto");

			world.alphaHost = test.api.forBand("alpha");
			world.bravoHost = test.api.forBand("bravo-secret");
			world.alphaAdminToken = fixtures.login(world.alphaHost, "alpha.admin@example.com");
			world.bothBandsAlphaToken = fixtures.login(world.alphaHost, "both.bands@example.com");
			return world;
		}

		IsolationScenarios.Target bravoTarget() {
			return new IsolationScenarios.Target(bravo.toString(), "bravo-secret",
					Map.of("memberId", bravoMember.toString()));
		}

		IsolationScenarios.Target alphaTarget() {
			return new IsolationScenarios.Target(alpha.toString(), "alpha", Map.of("memberId", alphaMember.toString()));
		}

		/** Strings that must never appear in a response to band A's requests. */
		List<String> bravoSecrets() {
			return List.of(bravo.toString(), "Bravo Secret Band", bravoMember.toString(), "Bruno Bravo Secreto",
					bravoAdmin.toString(), "bravo.admin@example.com");
		}

	}

}

package com.burstintofire.bandcore.tenancy.creation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.PostgresTestDatabase;
import com.burstintofire.bandcore.support.TestFixtures;
import com.burstintofire.bandcore.tenancy.BandCreated;

@Import(BandCreationIT.Listener.class)
class BandCreationIT extends IntegrationTest {

	static final List<BandCreated> EVENTS = new CopyOnWriteArrayList<>();

	UUID ana;

	String token;

	@BeforeEach
	void verifiedUserWithACentralSession() {
		EVENTS.clear();
		ana = fixtures.verifiedUser("ana@example.com");
		token = fixtures.login(api.central(), "ana@example.com");
	}

	@Test
	void verifiedUserCreatesABandAndBecomesItsAdminAndFirstMember() {
		ResponseEntity<String> response = create("The Burst", "The-Burst");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<String>read(response.getBody(), "$.slug")).isEqualTo("the-burst");
		assertThat(JsonPath.<String>read(response.getBody(), "$.name")).isEqualTo("The Burst");
		assertThat(JsonPath.<String>read(response.getBody(), "$.url")).isEqualTo("http://the-burst.localhost:3000");
		assertThat(response.getHeaders().getLocation()).hasToString("http://the-burst.localhost:3000");
		UUID bandId = UUID.fromString(JsonPath.read(response.getBody(), "$.id"));

		assertThat(inBand(bandId, "SELECT role || ' ' || status FROM bandcore.memberships WHERE user_id = '" + ana + "'"))
			.isEqualTo("ADMIN ACTIVE");
		assertThat(inBand(bandId, "SELECT display_name FROM bandcore.members WHERE user_id = '" + ana + "'"))
			.isEqualTo("Ana");
		assertThat(EVENTS).containsExactly(new BandCreated(bandId, "the-burst", ana));
	}

	@Test
	void newBandIsReadyToLogInToImmediately() {
		create("The Burst", "the-burst");

		ResponseEntity<String> login = api.forBand("the-burst")
			.postJson("/api/auth/login",
					"{\"email\": \"ana@example.com\", \"password\": \"%s\"}".formatted(TestFixtures.PASSWORD));

		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void takenReservedAndInvalidSlugsAreRejected() {
		create("The Burst", "the-burst");

		assertProblem(create("Other", "THE-BURST"), HttpStatus.CONFLICT, "SLUG_TAKEN");
		assertProblem(create("Other", "admin"), HttpStatus.UNPROCESSABLE_CONTENT, "SLUG_RESERVED");
		assertProblem(create("Other", "no"), HttpStatus.UNPROCESSABLE_CONTENT, "SLUG_INVALID");
		assertThat(JsonPath.<String>read(create("Other", "no").getBody(), "$.errors[0].field")).isEqualTo("slug");
	}

	@Test
	void unverifiedUserCannotCreateABand() {
		fixtures.user("pending@example.com", TestFixtures.PASSWORD, false);
		String pendingToken = fixtures.login(api.central(), "pending@example.com");

		ResponseEntity<String> response = api.central()
			.postJson("/api/bands", body("Band", "pending-band"), headers -> headers.setBearerAuth(pendingToken));

		assertProblem(response, HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
		assertThat(owner("SELECT count(*) FROM bandcore.bands")).isEqualTo("0");
	}

	@Test
	void bandCreationIsNotAvailableOnBandHosts() {
		UUID existing = fixtures.band("existing");
		fixtures.membership(existing, ana, "ADMIN");
		String bandToken = fixtures.login(api.forBand("existing"), "ana@example.com");

		ResponseEntity<String> response = api.forBand("existing")
			.postJson("/api/bands", body("Band", "from-band-host"), headers -> headers.setBearerAuth(bandToken));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void anonymousRequestsAreUnauthorized() {
		assertThat(api.central().postJson("/api/bands", body("Band", "anonymous")).getStatusCode())
			.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(api.central().get("/api/bands/slug-availability?slug=x").getStatusCode())
			.isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void concurrentCreationsOfTheSameSlugCreateExactlyOneBand() throws Exception {
		int attempts = 6;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ResponseEntity<String>>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
			for (int i = 0; i < attempts; i++) {
				Callable<ResponseEntity<String>> attempt = () -> {
					start.await();
					return create("Race", "race");
				};
				results.add(pool.submit(attempt));
			}
			start.countDown();
			List<HttpStatus> statuses = new ArrayList<>();
			for (Future<ResponseEntity<String>> result : results) {
				statuses.add(HttpStatus.valueOf(result.get().getStatusCode().value()));
			}

			assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED);
			assertThat(statuses).filteredOn(status -> status != HttpStatus.CREATED).containsOnly(HttpStatus.CONFLICT);
		}
		assertThat(owner("SELECT count(*) FROM bandcore.bands")).isEqualTo("1");
	}

	@Test
	void eachUserCanCreateALimitedNumberOfBands() {
		for (int i = 0; i < 5; i++) {
			OwnerSql.global("""
					INSERT INTO bandcore.bands (id, slug, name, created_by, created_at, updated_at)
					VALUES (gen_random_uuid(), 'band-%d', 'Band', '%s', now(), now())
					""".formatted(i, ana));
		}

		assertProblem(create("Sixth", "sixth"), HttpStatus.UNPROCESSABLE_CONTENT, "BAND_LIMIT_REACHED");
	}

	@Test
	void aFailureInAnyStepRollsBackEverything() {
		ResponseEntity<String> response = create("Boom", "boom");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(everyBand("SELECT count(*) FROM bandcore.bands")).isEqualTo("0");
		assertThat(everyBand("SELECT count(*) FROM bandcore.memberships")).isEqualTo("0");
		assertThat(everyBand("SELECT count(*) FROM bandcore.members")).isEqualTo("0");
	}

	@Test
	void slugAvailabilityExplainsWhyAnAddressCannotBeUsed() {
		create("The Burst", "the-burst");

		assertAvailability("Free-Name", "free-name", true, null);
		assertAvailability("the-burst", "the-burst", false, "SLUG_TAKEN");
		assertAvailability("www", "www", false, "SLUG_RESERVED");
		assertAvailability("a", "a", false, "SLUG_INVALID");
	}

	@Test
	void myBandsListsOnlyTheUsersActiveBands() {
		create("Zeta", "zeta");
		create("Alpha", "alpha");
		UUID other = fixtures.band("other");
		fixtures.membership(other, fixtures.verifiedUser("bia@example.com"), "ADMIN");

		ResponseEntity<String> response = api.central().get("/api/me/bands", headers -> headers.setBearerAuth(token));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<List<String>>read(response.getBody(), "$[*].slug")).containsExactly("alpha", "zeta");
		assertThat(JsonPath.<List<String>>read(response.getBody(), "$[*].role")).containsOnly("ADMIN");
		assertThat(response.getBody()).doesNotContain("other");
	}

	private ResponseEntity<String> create(String name, String slug) {
		return api.central().postJson("/api/bands", body(name, slug), headers -> headers.setBearerAuth(token));
	}

	private void assertAvailability(String input, String slug, boolean available, String reason) {
		ResponseEntity<String> response = api.central()
			.get("/api/bands/slug-availability?slug=" + input, headers -> headers.setBearerAuth(token));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(response.getBody(), "$.slug")).isEqualTo(slug);
		assertThat(JsonPath.<Boolean>read(response.getBody(), "$.available")).isEqualTo(available);
		assertThat(JsonPath.<String>read(response.getBody(), "$.reason")).isEqualTo(reason);
	}

	private static String body(String name, String slug) {
		return "{\"name\": \"%s\", \"slug\": \"%s\"}".formatted(name, slug);
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

	private static String inBand(UUID bandId, String sql) {
		return query("SELECT set_config('app.band_id', '" + bandId + "', false)", sql);
	}

	/** Rows of every band at once (as the superuser, which bypasses RLS). */
	private static String everyBand(String sql) {
		try (var connection = PostgresTestDatabase.superuserConnection(); var statement = connection.createStatement();
				var rs = statement.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String owner(String sql) {
		return query("SELECT 1", sql);
	}

	private static String query(String setup, String sql) {
		try (var connection = PostgresTestDatabase.ownerConnection(); var statement = connection.createStatement()) {
			statement.execute(setup);
			try (var rs = statement.executeQuery(sql)) {
				return rs.next() ? rs.getString(1) : null;
			}
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Listener {

		@Bean
		BandCreatedRecorder bandCreatedRecorder() {
			return new BandCreatedRecorder();
		}

	}

	/** Records events and fails for the band "boom", to prove the creation is atomic. */
	static class BandCreatedRecorder {

		@EventListener
		void on(BandCreated event) {
			if (event.slug().equals("boom")) {
				throw new IllegalStateException("listener failure");
			}
			EVENTS.add(event);
		}

	}

}

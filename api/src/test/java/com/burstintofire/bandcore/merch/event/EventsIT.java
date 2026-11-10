package com.burstintofire.bandcore.merch.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class EventsIT extends IntegrationTest {

	private static final String CREATE = """
			{"name": "Show no Centro", "venue": "Casa de Música", "city": "São Paulo",
			 "startsAt": "2026-02-01T22:00:00-03:00"}""";

	UUID alpha;

	ApiClient band;

	String admin;

	String member;

	@BeforeEach
	void bandWithAdminAndMember() {
		alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		fixtures.membership(alpha, fixtures.verifiedUser("bia@example.com"), "MEMBER");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		member = fixtures.login(band, "bia@example.com");
	}

	@Test
	void membersCanCreateEditAndReadEventsWithUpcomingAndPastListings() {
		ResponseEntity<String> created = band.postJson("/api/events", CREATE,
				headers -> headers.setBearerAuth(member));

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = JsonPath.read(created.getBody(), "$.id");
		assertThat(created.getHeaders().getLocation()).hasToString("/api/events/" + id);
		assertThat(JsonPath.<String>read(created.getBody(), "$.status")).isEqualTo("OPEN");
		assertThat(JsonPath.<String>read(created.getBody(), "$.startsAt")).isEqualTo("2026-02-02T01:00:00Z");
		assertThat(JsonPath.<Object>read(created.getBody(), "$.closedAt")).isNull();
		assertThat(JsonPath.<String>read(created.getBody(), "$.name")).isEqualTo("Show no Centro");

		ResponseEntity<String> updated = band.patchJson("/api/events/" + id, """
				{"version": 0, "name": "Festival", "venue": "", "startsAt": "2026-01-10T20:00:00Z"}""",
				headers -> headers.setBearerAuth(member));
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(updated.getBody(), "$.name")).isEqualTo("Festival");
		assertThat(JsonPath.<Object>read(updated.getBody(), "$.venue")).isNull();
		assertThat(JsonPath.<Integer>read(updated.getBody(), "$.version")).isEqualTo(1);

		fixtures.event(alpha, "Show passado", Instant.parse("2026-01-01T20:00:00Z"));
		fixtures.event(alpha, "Show futuro", Instant.parse("2026-02-01T20:00:00Z"));
		assertThat(JsonPath.<List<String>>read(
				band.get("/api/events", headers -> headers.setBearerAuth(member)).getBody(), "$[*].name"))
			.containsExactly("Show futuro");
		assertThat(JsonPath.<List<String>>read(
				band.get("/api/events?period=PAST", headers -> headers.setBearerAuth(member)).getBody(), "$[*].name"))
			.containsExactly("Festival", "Show passado");
		assertThat(JsonPath.<List<String>>read(
				band.get("/api/events?period=ALL", headers -> headers.setBearerAuth(member)).getBody(), "$[*].name"))
			.containsExactly("Show passado", "Festival", "Show futuro");

		ResponseEntity<String> fetched = band.get("/api/events/" + id, headers -> headers.setBearerAuth(member));
		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(fetched.getBody(), "$.id")).isEqualTo(id);
		assertThat(auditActions("event.%")).containsExactly("event.created", "event.updated");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'event.updated'"))
			.contains("\"name\": {\"to\": \"Festival\", \"from\": \"Show no Centro\"}");
	}

	@Test
	void invalidEventsAreRefusedAndStaleUpdatesConflict() {
		assertProblem(band.postJson("/api/events", """
				{"name": "  ", "startsAt": "2026-02-01T22:00:00Z"}""",
				headers -> headers.setBearerAuth(admin)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(band.postJson("/api/events", """
				{"name": "Sem data"}""", headers -> headers.setBearerAuth(admin)),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(band.postJson("/api/events", """
				{"name": "%s", "startsAt": "2026-02-01T22:00:00Z"}""".formatted("x".repeat(121)),
				headers -> headers.setBearerAuth(admin)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.events")).isEqualTo("0");

		String id = JsonPath.read(band.postJson("/api/events", CREATE,
				headers -> headers.setBearerAuth(admin)).getBody(), "$.id");
		band.patchJson("/api/events/" + id, "{\"version\": 0, \"name\": \"Updated\"}",
				headers -> headers.setBearerAuth(admin));
		assertProblem(band.patchJson("/api/events/" + id, "{\"version\": 0, \"name\": \"Stale\"}",
				headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	@Test
	void eventsAreIsolatedBetweenBands() {
		UUID bravo = fixtures.band("bravo");
		UUID bravoAdmin = fixtures.verifiedUser("caio@example.com");
		fixtures.membership(bravo, bravoAdmin, "ADMIN");
		UUID event = fixtures.event(bravo, "Segredo do Bravo", Instant.parse("2026-02-01T20:00:00Z"));

		ResponseEntity<String> response = band.get("/api/events/" + event,
				headers -> headers.setBearerAuth(admin));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).doesNotContain("Segredo do Bravo", bravo.toString());
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

	private static List<String> auditActions(String like) {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY action) FROM bandcore.audit_events WHERE action LIKE '%s'"
					.formatted(like));
		return actions == null ? List.of() : List.of(actions.split(","));
	}

}

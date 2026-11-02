package com.burstintofire.bandcore.tenancy.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;
import com.burstintofire.bandcore.support.PostgresTestDatabase;

class MemberAdministrationIT extends IntegrationTest {

	UUID alpha;

	UUID ana;

	ApiClient band;

	String token;

	@BeforeEach
	void adminOfAlpha() {
		alpha = fixtures.band("alpha");
		ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		band = api.forBand("alpha");
		token = fixtures.login(band, "ana@example.com");
	}

	@Test
	void anAdminAddsEditsAndListsMembers() {
		ResponseEntity<String> created = post("/api/members", "{\"displayName\": \"  Bia  \", \"instrument\": \"Vocal\"}");

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = JsonPath.read(created.getBody(), "$.id");
		assertThat(created.getHeaders().getLocation()).hasToString("/api/members/" + id);
		assertThat(JsonPath.<String>read(created.getBody(), "$.displayName")).isEqualTo("Bia");
		assertThat(JsonPath.<String>read(created.getBody(), "$.instrument")).isEqualTo("Vocal");
		assertThat(JsonPath.<Boolean>read(created.getBody(), "$.linkedToAccount")).isFalse();
		assertThat(JsonPath.<String>read(created.getBody(), "$.status")).isEqualTo("ACTIVE");

		ResponseEntity<String> updated = band.patchJson("/api/members/" + id, "{\"instrument\": \"\"}", this::auth);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<Object>read(updated.getBody(), "$.instrument")).isNull();
		assertThat(JsonPath.<String>read(updated.getBody(), "$.displayName")).isEqualTo("Bia");
		assertThat(JsonPath.<java.util.List<String>>read(band.get("/api/members", this::auth).getBody(), "$[*].displayName"))
			.containsExactly("Ana", "Bia");
		assertThat(auditActions()).containsExactly("member.created", "member.updated");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'member.updated'"))
			.isEqualTo("{\"instrument\": {\"to\": null, \"from\": \"Vocal\"}}");
	}

	@Test
	void invalidInputIsRejected() {
		assertProblem(post("/api/members", "{\"displayName\": \"  \"}"), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(post("/api/members", "{\"displayName\": \"" + "x".repeat(81) + "\"}"), HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED");
		assertProblem(post("/api/members", "{\"displayName\": \"Bia\", \"instrument\": \"" + "x".repeat(41) + "\"}"),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		UUID bia = fixtures.member(alpha, "Bia");
		assertProblem(band.patchJson("/api/members/" + bia, "{\"displayName\": \" \"}", this::auth),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
	}

	@Test
	void deactivatingKeepsTheMemberAndActivatingBringsThemBack() {
		UUID bia = fixtures.member(alpha, "Bia");

		ResponseEntity<String> deactivated = post("/api/members/" + bia + "/deactivate", null);

		assertThat(JsonPath.<String>read(deactivated.getBody(), "$.status")).isEqualTo("INACTIVE");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT left_at IS NOT NULL FROM bandcore.members WHERE id = '%s'".formatted(bia))).isEqualTo("t");
		assertThat(JsonPath.<String>read(post("/api/members/" + bia + "/activate", null).getBody(), "$.status"))
			.isEqualTo("ACTIVE");
		assertThat(auditActions()).containsExactly("member.activated", "member.deactivated");
	}

	@Test
	void aMemberWithoutHistoryCanBeDeleted() {
		UUID bia = fixtures.member(alpha, "Bia");

		ResponseEntity<String> deleted = band.send(org.springframework.http.HttpMethod.DELETE, "/api/members/" + bia,
				null, this::auth);

		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(band.get("/api/members/" + bia, this::auth).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(auditActions()).containsExactly("member.deleted");
	}

	@Test
	void aMemberWithHistoryCannotBeDeleted() throws Exception {
		UUID bia = fixtures.member(alpha, "Bia");
		// Stands in for expenses, splits and sales, which reference members the same way.
		try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement ddl = owner.createStatement()) {
			ddl.execute("""
					CREATE TABLE bandcore.test_member_history (band_id uuid NOT NULL, member_id uuid NOT NULL,
					    FOREIGN KEY (band_id, member_id) REFERENCES bandcore.members (band_id, id))
					""");
			try {
				ddl.execute("INSERT INTO bandcore.test_member_history VALUES ('%s', '%s')".formatted(alpha, bia));

				ResponseEntity<String> deleted = band.send(org.springframework.http.HttpMethod.DELETE,
						"/api/members/" + bia, null, this::auth);

				assertProblem(deleted, HttpStatus.CONFLICT, "MEMBER_HAS_HISTORY");
				assertThat(band.get("/api/members/" + bia, this::auth).getStatusCode()).isEqualTo(HttpStatus.OK);
				assertThat(auditActions()).isEmpty();
			}
			finally {
				ddl.execute("DROP TABLE bandcore.test_member_history");
			}
		}
	}

	@Test
	void aMemberIsLinkedOnlyToAnAccountWithAccessToTheBand() {
		UUID bia = fixtures.member(alpha, "Bia");
		UUID withAccess = fixtures.verifiedUser("bia@example.com");
		fixtures.membershipOnly(alpha, withAccess, "MEMBER");
		UUID outsider = fixtures.verifiedUser("outsider@example.com");

		assertProblem(link(bia, outsider), HttpStatus.UNPROCESSABLE_CONTENT, "USER_NOT_IN_BAND");
		assertProblem(link(bia, ana), HttpStatus.CONFLICT, "ACCOUNT_ALREADY_LINKED");

		ResponseEntity<String> linked = link(bia, withAccess);

		assertThat(JsonPath.<String>read(linked.getBody(), "$.userId")).isEqualTo(withAccess.toString());
		assertThat(JsonPath.<Boolean>read(linked.getBody(), "$.linkedToAccount")).isTrue();
		ResponseEntity<String> unlinked = band.send(org.springframework.http.HttpMethod.DELETE,
				"/api/members/" + bia + "/account", null, this::auth);
		assertThat(JsonPath.<Boolean>read(unlinked.getBody(), "$.linkedToAccount")).isFalse();
		assertThat(auditActions()).containsExactly("member.linked", "member.unlinked");
	}

	@Test
	void anAccountWhoseAccessWasRemovedCannotBeLinked() {
		UUID bia = fixtures.member(alpha, "Bia");
		UUID removed = fixtures.verifiedUser("removed@example.com");
		fixtures.membershipOnly(alpha, removed, "MEMBER");
		OwnerSql.inBand(alpha, "UPDATE bandcore.memberships SET status = 'REMOVED' WHERE user_id = '%s'".formatted(removed));

		assertProblem(link(bia, removed), HttpStatus.UNPROCESSABLE_CONTENT, "USER_NOT_IN_BAND");
	}

	private ResponseEntity<String> link(UUID member, UUID user) {
		return band.send(org.springframework.http.HttpMethod.PUT, "/api/members/" + member + "/account",
				"{\"userId\": \"%s\"}".formatted(user), this::auth);
	}

	private ResponseEntity<String> post(String path, String json) {
		return band.postJson(path, json, this::auth);
	}

	private void auth(org.springframework.http.HttpHeaders headers) {
		headers.setBearerAuth(token);
	}

	private java.util.List<String> auditActions() {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY occurred_at, action) FROM bandcore.audit_events");
		return actions == null ? java.util.List.of() : java.util.List.of(actions.split(","));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

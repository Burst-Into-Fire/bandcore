package com.burstintofire.bandcore.finance.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class ExpenseCategoriesIT extends IntegrationTest {

	UUID alpha;

	ApiClient band;

	String token;

	@BeforeEach
	void adminOfAlpha() {
		alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		band = api.forBand("alpha");
		token = fixtures.login(band, "ana@example.com");
	}

	@Test
	void aNewBandStartsWithTheDefaultCategories() {
		fixtures.verifiedUser("bia@example.com");
		String central = fixtures.login(api.central(), "bia@example.com");
		ResponseEntity<String> created = api.central()
			.postJson("/api/bands", "{\"name\": \"The Burst\", \"slug\": \"the-burst\"}",
					headers -> headers.setBearerAuth(central));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ApiClient burst = api.forBand("the-burst");
		String burstToken = fixtures.login(burst, "bia@example.com");
		String list = burst.get("/api/expense-categories", headers -> headers.setBearerAuth(burstToken)).getBody();

		assertThat(JsonPath.<List<String>>read(list, "$[*].defaultKey")).containsExactlyInAnyOrder("REHEARSAL_SPACE",
				"GEAR", "VIDEO", "PHOTOGRAPHY", "TRAVEL", "MERCH", "OTHER");
		assertThat(JsonPath.<List<String>>read(list, "$[*].name")).contains("Estúdio de ensaio", "Outros");
		assertThat(JsonPath.<List<Boolean>>read(list, "$[*].archived")).containsOnly(false);
	}

	@Test
	void anAdminCreatesRenamesRecolorsArchivesAndDeletesACategory() {
		ResponseEntity<String> created = band.postJson("/api/expense-categories",
				"{\"name\": \"  Ensaio aberto \", \"color\": \"#FF6B5A\"}", this::auth);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = JsonPath.read(created.getBody(), "$.id");
		assertThat(created.getHeaders().getLocation()).hasToString("/api/expense-categories/" + id);
		assertThat(JsonPath.<String>read(created.getBody(), "$.name")).isEqualTo("Ensaio aberto");
		assertThat(JsonPath.<String>read(created.getBody(), "$.color")).isEqualTo("#ff6b5a");
		assertThat(JsonPath.<Object>read(created.getBody(), "$.defaultKey")).isNull();

		ResponseEntity<String> updated = band.patchJson("/api/expense-categories/" + id,
				"{\"name\": \"Shows\", \"color\": \"\"}", this::auth);
		assertThat(JsonPath.<String>read(updated.getBody(), "$.name")).isEqualTo("Shows");
		assertThat(JsonPath.<Object>read(updated.getBody(), "$.color")).isNull();

		ResponseEntity<String> archived = band.postJson("/api/expense-categories/" + id + "/archive", null, this::auth);
		assertThat(JsonPath.<Boolean>read(archived.getBody(), "$.archived")).isTrue();
		assertThat(JsonPath.<List<Boolean>>read(band.get("/api/expense-categories", this::auth).getBody(),
				"$[?(@.name == 'Shows')].archived"))
			.containsExactly(true);
		ResponseEntity<String> restored = band.postJson("/api/expense-categories/" + id + "/unarchive", null,
				this::auth);
		assertThat(JsonPath.<Boolean>read(restored.getBody(), "$.archived")).isFalse();

		assertThat(band.send(org.springframework.http.HttpMethod.DELETE, "/api/expense-categories/" + id, null, this::auth).getStatusCode())
			.isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(band.get("/api/expense-categories", this::auth).getBody()).doesNotContain(id);
		assertThat(auditActions()).containsExactlyInAnyOrder("expense_category.created", "expense_category.updated",
				"expense_category.archived", "expense_category.unarchived", "expense_category.deleted");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'expense_category.updated'"))
			.contains("Ensaio aberto")
			.contains("Shows");
	}

	@Test
	void namesAreUniquePerBandRegardlessOfCaseArchivedOnesIncluded() {
		UUID gear = fixtures.category(alpha, "Gear");
		band.postJson("/api/expense-categories/" + gear + "/archive", null, this::auth);
		fixtures.category(alpha, "Travel");
		UUID other = fixtures.band("other");
		fixtures.category(other, "Shows");

		assertProblem(band.postJson("/api/expense-categories", "{\"name\": \"GEAR\"}", this::auth), HttpStatus.CONFLICT,
				"CATEGORY_NAME_TAKEN");
		UUID travel = UUID.fromString(OwnerSql.superuserQueryForString(
				"SELECT id FROM bandcore.expense_categories WHERE name = 'Travel'"));
		assertProblem(band.patchJson("/api/expense-categories/" + travel, "{\"name\": \"gear\"}", this::auth),
				HttpStatus.CONFLICT, "CATEGORY_NAME_TAKEN");
		// Another band's names do not count.
		assertThat(band.postJson("/api/expense-categories", "{\"name\": \"Shows\"}", this::auth).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
		// Changing only the case of a category's own name is allowed.
		assertThat(band.patchJson("/api/expense-categories/" + travel, "{\"name\": \"TRAVEL\"}", this::auth)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void renamingADefaultCategoryDropsItsTranslationKey() {
		OwnerSql.inBand(alpha, """
				INSERT INTO bandcore.expense_categories (id, band_id, name, default_key, created_at, updated_at)
				VALUES (gen_random_uuid(), '%s', 'Viagem', 'TRAVEL', now(), now())
				""".formatted(alpha));
		String id = JsonPath.read(band.get("/api/expense-categories", this::auth).getBody(), "$[0].id");

		ResponseEntity<String> recolored = band.patchJson("/api/expense-categories/" + id, "{\"color\": \"#00aa88\"}",
				this::auth);
		assertThat(JsonPath.<String>read(recolored.getBody(), "$.defaultKey")).isEqualTo("TRAVEL");

		ResponseEntity<String> renamed = band.patchJson("/api/expense-categories/" + id, "{\"name\": \"Turnê\"}",
				this::auth);
		assertThat(JsonPath.<Object>read(renamed.getBody(), "$.defaultKey")).isNull();
		assertThat(JsonPath.<String>read(renamed.getBody(), "$.name")).isEqualTo("Turnê");
	}

	@Test
	void invalidInputIsRejected() {
		assertProblem(band.postJson("/api/expense-categories", "{\"name\": \"  \"}", this::auth), HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED");
		assertProblem(band.postJson("/api/expense-categories", "{\"name\": \"x\", \"color\": \"red\"}", this::auth),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(band.postJson("/api/expense-categories", "{\"name\": \"%s\"}".formatted("x".repeat(41)),
				this::auth), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertThat(band.patchJson("/api/expense-categories/" + UUID.randomUUID(), "{\"name\": \"x\"}", this::auth)
			.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private void auth(HttpHeaders headers) {
		headers.setBearerAuth(token);
	}

	private List<String> auditActions() {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY occurred_at, id) FROM bandcore.audit_events");
		return actions == null ? List.of() : List.of(actions.split(","));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

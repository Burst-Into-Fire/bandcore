package com.burstintofire.bandcore.finance.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class ExpensesIT extends IntegrationTest {

	UUID alpha;

	UUID ana;

	UUID bia;

	UUID anaMember;

	UUID biaMember;

	UUID gear;

	ApiClient band;

	String admin;

	String member;

	@BeforeEach
	void bandWithAnAdminAndAMember() {
		alpha = fixtures.band("alpha");
		ana = fixtures.verifiedUser("ana@example.com");
		bia = fixtures.verifiedUser("bia@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.membership(alpha, bia, "MEMBER");
		anaMember = fixtures.memberOf(alpha, ana);
		biaMember = fixtures.memberOf(alpha, bia);
		gear = fixtures.category(alpha, "Equipamento");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		member = fixtures.login(band, "bia@example.com");
	}

	@Test
	void aMemberRecordsAPlannedExpenseWithAFutureDate() {
		ResponseEntity<String> created = create(member, """
				{"description": " Fotógrafo, ensaio de divulgação ", "categoryId": "%s", "currency": "BRL",
				 "amountCents": 35000, "expenseDate": "2027-01-25", "status": "PLANNED", "notes": "Sinal pago na hora"}
				""".formatted(gear));

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String body = created.getBody();
		String id = JsonPath.read(body, "$.id");
		assertThat(created.getHeaders().getLocation()).hasToString("/api/expenses/" + id);
		assertThat(JsonPath.<String>read(body, "$.description")).isEqualTo("Fotógrafo, ensaio de divulgação");
		assertThat(JsonPath.<Integer>read(body, "$.amountCents")).isEqualTo(35000);
		assertThat(JsonPath.<String>read(body, "$.expenseDate")).isEqualTo("2027-01-25");
		assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("PLANNED");
		assertThat(JsonPath.<Object>read(body, "$.paidByMemberId")).isNull();
		assertThat(JsonPath.<String>read(body, "$.createdBy")).isEqualTo(bia.toString());
		assertThat(JsonPath.<Integer>read(body, "$.version")).isZero();
		assertThat(JsonPath.<Boolean>read(body, "$.access.edit")).isTrue();
		assertThat(auditActions()).containsExactly("expense.created");
	}

	@Test
	void aPaidExpenseNeedsAnActivePayerOfTheSameBand() {
		ResponseEntity<String> noPayer = create(member, paid(null, 5000));
		assertProblem(noPayer, HttpStatus.UNPROCESSABLE_CONTENT, "PAYER_REQUIRED");
		assertThat(JsonPath.<String>read(noPayer.getBody(), "$.errors[0].field")).isEqualTo("paidByMemberId");

		UUID left = fixtures.member(alpha, "Caio");
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id = '%s'".formatted(left));
		assertProblem(create(member, paid(left, 5000)), HttpStatus.UNPROCESSABLE_CONTENT, "MEMBER_INACTIVE");

		UUID other = fixtures.band("other");
		UUID stranger = fixtures.member(other, "Stranger");
		UUID foreignCategory = fixtures.category(other, "Foreign");
		assertThat(create(member, paid(stranger, 5000)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(create(member, """
				{"description": "x", "categoryId": "%s", "currency": "BRL", "amountCents": 100,
				 "expenseDate": "2026-11-10", "status": "PLANNED"}""".formatted(foreignCategory)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<String> ok = create(member, paid(anaMember, 5000));
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<String>read(ok.getBody(), "$.paidByMemberId")).isEqualTo(anaMember.toString());
		assertThat(JsonPath.<String>read(ok.getBody(), "$.paidOn")).isEqualTo("2026-11-10");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.expenses")).isEqualTo("1");
	}

	@Test
	void invalidAmountsAndArchivedCategoriesAreRejected() {
		assertProblem(create(member, paid(anaMember, 0)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(create(member, paid(anaMember, -100)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(create(member, """
				{"description": "x", "categoryId": "%s", "currency": "EUR", "amountCents": 100,
				 "expenseDate": "2026-11-10", "status": "PLANNED"}""".formatted(gear)), HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST");
		assertProblem(create(member, """
				{"description": "x", "categoryId": "%s", "currency": "BRL", "amountCents": 10.5,
				 "expenseDate": "2026-11-10", "status": "PLANNED"}""".formatted(gear)), HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST");

		band.postJson("/api/expense-categories/" + gear + "/archive", null, headers -> headers.setBearerAuth(admin));
		ResponseEntity<String> archived = create(member, paid(anaMember, 100));
		assertProblem(archived, HttpStatus.UNPROCESSABLE_CONTENT, "CATEGORY_ARCHIVED");
		assertThat(JsonPath.<String>read(archived.getBody(), "$.errors[0].field")).isEqualTo("categoryId");
	}

	@Test
	void archivingACategoryInUseKeepsItsExpensesValid() {
		String id = JsonPath.read(create(member, paid(anaMember, 2500)).getBody(), "$.id");
		band.postJson("/api/expense-categories/" + gear + "/archive", null, headers -> headers.setBearerAuth(admin));

		ResponseEntity<String> read = band.get("/api/expenses/" + id, headers -> headers.setBearerAuth(member));
		assertThat(JsonPath.<String>read(read.getBody(), "$.categoryId")).isEqualTo(gear.toString());
		ResponseEntity<String> edited = band.patchJson("/api/expenses/" + id,
				"{\"version\": 0, \"description\": \"Cabos\"}", headers -> headers.setBearerAuth(member));
		assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertProblem(band.send(HttpMethod.DELETE, "/api/expense-categories/" + gear, null,
				headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT, "CATEGORY_IN_USE");
		assertProblem(band.send(HttpMethod.DELETE, "/api/members/" + anaMember, null,
				headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT, "MEMBER_HAS_HISTORY");
	}

	@Test
	void listsFilterSortAndPaginate() {
		UUID travel = fixtures.category(alpha, "Viagem");
		create(member, expense("Cordas", gear, "BRL", 4000, "2026-10-05", "PAID", anaMember));
		create(member, expense("Van para o show", travel, "BRL", 60000, "2026-11-12", "PAID", biaMember));
		create(member, expense("Pedal", gear, "USD", 15000, "2026-11-02", "PAID", anaMember));
		create(member, expense("Hotel", travel, "BRL", 30000, "2026-11-28", "PLANNED", null));

		assertThat(descriptions("")).containsExactly("Hotel", "Van para o show", "Pedal", "Cordas");
		assertThat(descriptions("month=2026-11")).containsExactly("Hotel", "Van para o show", "Pedal");
		assertThat(descriptions("status=PLANNED")).containsExactly("Hotel");
		assertThat(descriptions("categoryId=" + gear)).containsExactly("Pedal", "Cordas");
		assertThat(descriptions("payerMemberId=" + anaMember)).containsExactly("Pedal", "Cordas");
		assertThat(descriptions("currency=USD")).containsExactly("Pedal");
		assertThat(descriptions("q=SHOW")).containsExactly("Van para o show");
		assertThat(descriptions("q=%25")).isEmpty();
		assertThat(descriptions("sort=AMOUNT_DESC")).containsExactly("Van para o show", "Hotel", "Pedal", "Cordas");

		ResponseEntity<String> page = band.get("/api/expenses?size=3&page=1", headers -> headers.setBearerAuth(member));
		assertThat(JsonPath.<List<String>>read(page.getBody(), "$.items[*].description")).containsExactly("Cordas");
		assertThat(JsonPath.<Integer>read(page.getBody(), "$.totalItems")).isEqualTo(4);
		assertThat(JsonPath.<Integer>read(page.getBody(), "$.totalPages")).isEqualTo(2);
		assertThat(band.get("/api/expenses?size=101", headers -> headers.setBearerAuth(member)).getStatusCode())
			.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(band.get("/api/expenses?month=2026-13", headers -> headers.setBearerAuth(member)).getStatusCode())
			.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void membersChangeTheirOwnExpensesAndAdminsAnyExpense() {
		String byAna = JsonPath.read(create(admin, paid(anaMember, 1000)).getBody(), "$.id");
		String paidByBia = JsonPath.read(create(admin, paid(biaMember, 1000)).getBody(), "$.id");
		String byBia = JsonPath.read(create(member, paid(anaMember, 1000)).getBody(), "$.id");

		ResponseEntity<String> others = band.patchJson("/api/expenses/" + byAna, "{\"version\": 0, \"notes\": \"x\"}",
				headers -> headers.setBearerAuth(member));
		assertProblem(others, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		assertThat(JsonPath.<Boolean>read(band.get("/api/expenses/" + byAna, headers -> headers.setBearerAuth(member))
			.getBody(), "$.access.edit")).isFalse();
		assertThat(band.patchJson("/api/expenses/" + paidByBia, "{\"version\": 0, \"notes\": \"x\"}",
				headers -> headers.setBearerAuth(member)).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(band.patchJson("/api/expenses/" + byBia, "{\"version\": 0, \"notes\": \"x\"}",
				headers -> headers.setBearerAuth(member)).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(band.patchJson("/api/expenses/" + byBia, "{\"version\": 1, \"notes\": \"y\"}",
				headers -> headers.setBearerAuth(admin)).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertProblem(band.send(HttpMethod.DELETE, "/api/expenses/" + byAna, null,
				headers -> headers.setBearerAuth(member)), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
	}

	@Test
	void aStaleVersionIsAConflict() {
		String id = JsonPath.read(create(member, paid(anaMember, 1000)).getBody(), "$.id");

		ResponseEntity<String> first = band.patchJson("/api/expenses/" + id, "{\"version\": 0, \"amountCents\": 1200}",
				headers -> headers.setBearerAuth(member));
		ResponseEntity<String> second = band.patchJson("/api/expenses/" + id,
				"{\"version\": 0, \"amountCents\": 1500}", headers -> headers.setBearerAuth(admin));

		assertThat(JsonPath.<Integer>read(first.getBody(), "$.version")).isEqualTo(1);
		assertProblem(second, HttpStatus.CONFLICT, "VERSION_CONFLICT");
		assertThat(JsonPath.<Integer>read(band.get("/api/expenses/" + id, headers -> headers.setBearerAuth(member))
			.getBody(), "$.amountCents")).isEqualTo(1200);
		assertProblem(band.send(HttpMethod.DELETE, "/api/expenses/" + id + "?version=0", null,
				headers -> headers.setBearerAuth(member)), HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	@Test
	void editsAreAuditedWithADiff() {
		String id = JsonPath.read(create(member, paid(anaMember, 1000)).getBody(), "$.id");

		band.patchJson("/api/expenses/" + id, "{\"version\": 0, \"amountCents\": 1250, \"paidByMemberId\": \"%s\"}"
			.formatted(biaMember), headers -> headers.setBearerAuth(member));
		// Nothing changed: no new version, no audit record.
		ResponseEntity<String> same = band.patchJson("/api/expenses/" + id, "{\"version\": 1, \"amountCents\": 1250}",
				headers -> headers.setBearerAuth(member));

		assertThat(JsonPath.<Integer>read(same.getBody(), "$.version")).isEqualTo(1);
		String diff = OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'expense.updated'");
		assertThat(diff).contains("\"amount_cents\": {\"to\": 1250, \"from\": 1000}")
			.contains("\"paid_by_member_id\"")
			.doesNotContain("description");
	}

	@Test
	void changingWhoPaidAPlannedExpenseIsRefused() {
		String id = JsonPath.read(create(member, expense("Hotel", gear, "BRL", 100, "2026-12-01", "PLANNED", null))
			.getBody(), "$.id");

		assertProblem(band.patchJson("/api/expenses/" + id, "{\"version\": 0, \"paidByMemberId\": \"%s\"}"
			.formatted(anaMember), headers -> headers.setBearerAuth(member)), HttpStatus.CONFLICT, "EXPENSE_NOT_PAID");
	}

	@Test
	void paidExpensesAreSoftDeletedAndPlannedOnesDeleted() {
		String paid = JsonPath.read(create(member, paid(anaMember, 1000)).getBody(), "$.id");
		String planned = JsonPath.read(create(member, expense("Hotel", gear, "BRL", 100, "2026-12-01", "PLANNED", null))
			.getBody(), "$.id");

		assertThat(band.send(HttpMethod.DELETE, "/api/expenses/" + paid + "?version=0", null,
				headers -> headers.setBearerAuth(member)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(band.send(HttpMethod.DELETE, "/api/expenses/" + planned, null,
				headers -> headers.setBearerAuth(member)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(band.get("/api/expenses/" + paid, headers -> headers.setBearerAuth(member)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(descriptions("")).isEmpty();
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT count(*) FROM bandcore.expenses WHERE id = '%s' AND deleted_at IS NOT NULL".formatted(paid)))
			.isEqualTo("1");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT count(*) FROM bandcore.expenses WHERE id = '%s'".formatted(planned)))
			.isEqualTo("0");
		assertThat(auditActions()).containsExactlyInAnyOrder("expense.created", "expense.created", "expense.deleted",
				"expense.deleted");
	}

	private ResponseEntity<String> create(String token, String json) {
		return band.postJson("/api/expenses", json, headers -> headers.setBearerAuth(token));
	}

	private String paid(UUID payer, long cents) {
		return expense("Cabos", gear, "BRL", cents, "2026-11-10", "PAID", payer);
	}

	private static String expense(String description, UUID category, String currency, long cents, String date,
			String status, UUID payer) {
		return """
				{"description": "%s", "categoryId": "%s", "currency": "%s", "amountCents": %d,
				 "expenseDate": "%s", "status": "%s"%s}""".formatted(description, category, currency, cents, date, status,
				payer == null ? "" : ", \"paidByMemberId\": \"%s\"".formatted(payer));
	}

	private List<String> descriptions(String query) {
		ResponseEntity<String> response = band.get("/api/expenses?" + query, this::asMember);
		assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.OK);
		return JsonPath.read(response.getBody(), "$.items[*].description");
	}

	private void asMember(HttpHeaders headers) {
		headers.setBearerAuth(member);
	}

	private List<String> auditActions() {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY action) FROM bandcore.audit_events WHERE action LIKE 'expense.%'");
		return actions == null ? List.of() : List.of(actions.split(","));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

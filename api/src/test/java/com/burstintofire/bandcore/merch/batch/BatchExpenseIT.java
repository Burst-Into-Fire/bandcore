package com.burstintofire.bandcore.merch.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** A production batch's cost becomes a split expense, saved together with the batch (M7-04). */
class BatchExpenseIT extends IntegrationTest {

	UUID alpha;

	UUID anaMember;

	UUID biaMember;

	UUID merch;

	UUID medium;

	ApiClient band;

	String admin;

	String bia;

	@BeforeEach
	void bandWithThreeMembersAndAShirt() {
		alpha = fixtures.band("alpha");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		UUID biaUser = fixtures.verifiedUser("bia@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.membership(alpha, biaUser, "MEMBER");
		fixtures.membership(alpha, fixtures.verifiedUser("caio@example.com"), "MEMBER");
		anaMember = fixtures.memberOf(alpha, ana);
		biaMember = fixtures.memberOf(alpha, biaUser);
		merch = fixtures.category(alpha, "Merch");
		medium = fixtures.variant(alpha, fixtures.product(alpha, "Camiseta preta"), "M");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		bia = fixtures.login(band, "bia@example.com");
	}

	@Test
	void aBatchWithAnExpenseCreatesExactlyOneSplitExpenseInTheBand() {
		ResponseEntity<String> registered = register("""
				{"description": "Lote de camisetas", "categoryId": "%s", "paid": true, "paidByMemberId": "%s",
				 "split": {"mode": "EQUAL"}}""".formatted(merch, anaMember));

		assertThat(registered.getStatusCode()).as(registered.getBody()).isEqualTo(HttpStatus.CREATED);
		String batchId = JsonPath.read(registered.getBody(), "$.id");
		assertThat(JsonPath.<Boolean>read(registered.getBody(), "$.expenseRequested")).isTrue();

		String expenses = band.get("/api/expenses", headers -> headers.setBearerAuth(bia)).getBody();
		assertThat(JsonPath.<List<Object>>read(expenses, "$.items[*]")).hasSize(1);
		assertThat(JsonPath.<String>read(expenses, "$.items[0].description")).isEqualTo("Lote de camisetas");
		assertThat(JsonPath.<Integer>read(expenses, "$.items[0].amountCents")).isEqualTo(30_000);
		assertThat(JsonPath.<String>read(expenses, "$.items[0].currency")).isEqualTo("BRL");
		assertThat(JsonPath.<String>read(expenses, "$.items[0].status")).isEqualTo("PAID");
		assertThat(JsonPath.<String>read(expenses, "$.items[0].paidByMemberId")).isEqualTo(anaMember.toString());
		// Without its own date, the expense is dated when the batch was received (the test clock's today).
		assertThat(JsonPath.<String>read(expenses, "$.items[0].expenseDate")).isEqualTo("2026-01-15");
		assertThat(JsonPath.<String>read(expenses, "$.items[0].source.type")).isEqualTo("BATCH");
		assertThat(JsonPath.<String>read(expenses, "$.items[0].source.id")).isEqualTo(batchId);
		assertThat(JsonPath.<List<Integer>>read(expenses, "$.items[0].split.shares[*].shareCents"))
			.containsExactly(10_000, 10_000, 10_000);
		// Row-level security put it in the batch's band.
		assertThat(OwnerSql.superuserQueryForString("SELECT band_id FROM bandcore.expenses")).isEqualTo(alpha.toString());
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes ->> 'source_id' FROM bandcore.audit_events WHERE action = 'expense.created'"))
			.isEqualTo(batchId);
	}

	@Test
	void aCustomSplitAndAPlannedExpenseWork() {
		ResponseEntity<String> registered = register("""
				{"description": "Lote de camisetas", "categoryId": "%s", "expenseDate": "2026-02-10",
				 "split": {"mode": "CUSTOM_AMOUNTS", "shares": [{"memberId": "%s", "amountCents": 20000},
				                                                {"memberId": "%s", "amountCents": 10000}]}}"""
			.formatted(merch, anaMember, biaMember));

		assertThat(registered.getStatusCode()).as(registered.getBody()).isEqualTo(HttpStatus.CREATED);
		String expenses = band.get("/api/expenses", headers -> headers.setBearerAuth(bia)).getBody();
		assertThat(JsonPath.<String>read(expenses, "$.items[0].status")).isEqualTo("PLANNED");
		assertThat(JsonPath.<String>read(expenses, "$.items[0].expenseDate")).isEqualTo("2026-02-10");
		assertThat(JsonPath.<List<Integer>>read(expenses, "$.items[0].split.shares[*].shareCents"))
			.containsExactlyInAnyOrder(20_000, 10_000);
	}

	@Test
	void aBatchWithoutAnExpenseCreatesNone() {
		ResponseEntity<String> registered = band.postJson("/api/batches", batch(null),
				headers -> headers.setBearerAuth(admin));

		assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<Boolean>read(registered.getBody(), "$.expenseRequested")).isFalse();
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.expenses")).isEqualTo("0");
	}

	@Test
	void anExpenseTheRulesRefuseSavesNothing() {
		assertProblem(register("""
				{"description": "Lote", "categoryId": "%s",
				 "split": {"mode": "CUSTOM_AMOUNTS", "shares": [{"memberId": "%s", "amountCents": 100}]}}"""
			.formatted(merch, anaMember)), HttpStatus.UNPROCESSABLE_CONTENT, "SPLIT_SUM_MISMATCH", "expense.split");
		assertProblem(register("""
				{"description": "Lote", "categoryId": "%s", "paid": true}""".formatted(merch)),
				HttpStatus.UNPROCESSABLE_CONTENT, "PAYER_REQUIRED", "expense.paidByMemberId");
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id = '%s'".formatted(biaMember));
		assertProblem(register("""
				{"description": "Lote", "categoryId": "%s", "paid": true, "paidByMemberId": "%s"}"""
			.formatted(merch, biaMember)), HttpStatus.UNPROCESSABLE_CONTENT, "MEMBER_INACTIVE", "expense.paidByMemberId");
		OwnerSql.inBand(alpha, "UPDATE bandcore.expense_categories SET archived_at = now() WHERE id = '%s'".formatted(merch));
		assertProblem(register("""
				{"description": "Lote", "categoryId": "%s"}""".formatted(merch)), HttpStatus.UNPROCESSABLE_CONTENT,
				"CATEGORY_ARCHIVED", "expense.categoryId");
		UUID theirs = fixtures.category(fixtures.band("bravo"), "Merch");
		assertThat(register("""
				{"description": "Lote", "categoryId": "%s"}""".formatted(theirs)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertProblem(band.postJson("/api/batches", """
				{"currency": "BRL", "costMode": "PER_ITEM",
				 "items": [{"variantId": "%s", "quantity": 10, "unitCostCents": 0}],
				 "expense": {"description": "Brinde", "categoryId": "%s"}}""".formatted(medium, merch),
				headers -> headers.setBearerAuth(admin)), HttpStatus.UNPROCESSABLE_CONTENT, "BATCH_COST_REQUIRED", "expense");

		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.production_batches")).isEqualTo("0");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.stock_movements")).isEqualTo("0");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.expenses")).isEqualTo("0");
	}

	@Test
	void aBatchHasAtMostOneExpense() {
		String batchId = JsonPath.read(register("""
				{"description": "Lote", "categoryId": "%s"}""".formatted(merch)).getBody(), "$.id");

		assertThatThrownBy(() -> OwnerSql.inBand(alpha, """
				INSERT INTO bandcore.expenses (id, band_id, description, category_id, currency, amount_cents, expense_date,
				    status, split_mode, created_by, created_at, updated_at, source_type, source_id)
				VALUES (gen_random_uuid(), '%s', 'Outro', '%s', 'BRL', 100, DATE '2026-01-15', 'PLANNED', 'EQUAL',
				    gen_random_uuid(), now(), now(), 'BATCH', '%s')
				""".formatted(alpha, merch, batchId)))
			.hasStackTraceContaining("expenses_source_key");
	}

	@Test
	void cancellingTheBatchLeavesItsExpenseAlone() {
		String batchId = JsonPath.read(register("""
				{"description": "Lote", "categoryId": "%s"}""".formatted(merch)).getBody(), "$.id");

		ResponseEntity<String> cancelled = band.postJson("/api/batches/" + batchId + "/cancel", "{\"version\": 0}",
				headers -> headers.setBearerAuth(admin));

		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<Boolean>read(cancelled.getBody(), "$.expenseRequested")).isTrue();
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT amount_cents FROM bandcore.expenses WHERE deleted_at IS NULL")).isEqualTo("30000");
	}

	private ResponseEntity<String> register(String expense) {
		return band.postJson("/api/batches", batch(expense), headers -> headers.setBearerAuth(admin));
	}

	/** Ten shirts at R$ 30,00, with an expense when given. */
	private String batch(String expense) {
		return """
				{"currency": "BRL", "costMode": "PER_ITEM",
				 "items": [{"variantId": "%s", "quantity": 10, "unitCostCents": 3000}]%s}""".formatted(medium,
				expense == null ? "" : ", \"expense\": " + expense);
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code, String field) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
		assertThat(JsonPath.<String>read(response.getBody(), "$.errors[0].field")).isEqualTo(field);
	}

}

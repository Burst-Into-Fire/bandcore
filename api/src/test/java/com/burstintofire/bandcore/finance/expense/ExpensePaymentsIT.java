package com.burstintofire.bandcore.finance.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import com.burstintofire.bandcore.support.TestClockConfiguration;

/** The test clock says 2026-01-15T15:00Z: 12:00 on 2026-01-15 in São Paulo. */
class ExpensePaymentsIT extends IntegrationTest {

	UUID alpha;

	UUID anaMember;

	UUID biaMember;

	UUID caioMember;

	UUID gear;

	ApiClient band;

	String admin;

	String bia;

	String caio;

	@BeforeEach
	void bandWithAnAdminAndTwoMembers() {
		alpha = fixtures.band("alpha");
		UUID ana = fixtures.verifiedUser("ana@example.com");
		UUID biaUser = fixtures.verifiedUser("bia@example.com");
		UUID caioUser = fixtures.verifiedUser("caio@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.membership(alpha, biaUser, "MEMBER");
		fixtures.membership(alpha, caioUser, "MEMBER");
		anaMember = fixtures.memberOf(alpha, ana);
		biaMember = fixtures.memberOf(alpha, biaUser);
		caioMember = fixtures.memberOf(alpha, caioUser);
		gear = fixtures.category(alpha, "Fotografia");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		bia = fixtures.login(band, "bia@example.com");
		caio = fixtures.login(band, "caio@example.com");
	}

	@Test
	void theCreatorMarksAPlannedExpenseAsPaidBySomeone() {
		String id = planned(bia, "Fotógrafo", "2026-01-25");

		ResponseEntity<String> paid = pay(bia, id, "{\"payerMemberId\": \"%s\", \"paidOn\": \"2026-01-24\", \"version\": 0}"
			.formatted(anaMember));

		assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(paid.getBody(), "$.status")).isEqualTo("PAID");
		assertThat(JsonPath.<String>read(paid.getBody(), "$.paidByMemberId")).isEqualTo(anaMember.toString());
		assertThat(JsonPath.<String>read(paid.getBody(), "$.paidOn")).isEqualTo("2026-01-24");
		assertThat(JsonPath.<Integer>read(paid.getBody(), "$.version")).isEqualTo(1);
		assertThat(JsonPath.<Boolean>read(paid.getBody(), "$.access.pay")).isFalse();
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'expense.paid'"))
			.contains(anaMember.toString())
			.contains("2026-01-24");
	}

	@Test
	void anyMemberPaysSomeoneElsesPlannedExpenseThemselvesTodayByDefault() {
		String id = planned(bia, "Estúdio", "2026-01-20");
		assertThat(JsonPath.<Boolean>read(get(caio, id), "$.access.pay")).isTrue();
		assertThat(JsonPath.<Boolean>read(get(caio, id), "$.access.edit")).isFalse();

		// Caio can pay it himself, not record that Ana paid it.
		ResponseEntity<String> forAna = pay(caio, id, "{\"payerMemberId\": \"%s\"}".formatted(anaMember));
		assertProblem(forAna, HttpStatus.FORBIDDEN, "ACCESS_DENIED");

		ResponseEntity<String> himself = pay(caio, id, "{}");
		assertThat(JsonPath.<String>read(himself.getBody(), "$.paidByMemberId")).isEqualTo(caioMember.toString());
		assertThat(JsonPath.<String>read(himself.getBody(), "$.paidOn")).isEqualTo("2026-01-15");
		// Having paid it, the expense is now his own to edit.
		assertThat(JsonPath.<Boolean>read(himself.getBody(), "$.access.edit")).isTrue();
	}

	@Test
	void invalidTransitionsAreConflicts() {
		String id = planned(bia, "Estúdio", "2026-01-20");
		pay(bia, id, "{}");

		assertProblem(pay(bia, id, "{}"), HttpStatus.CONFLICT, "EXPENSE_NOT_PLANNED");
		String other = planned(bia, "Hotel", "2026-02-01");
		assertProblem(revert(admin, other), HttpStatus.CONFLICT, "EXPENSE_NOT_PAID");
		assertProblem(pay(bia, other, "{\"version\": 7}"), HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	@Test
	void theCallerNeedsAMemberOrAPayerAndThePayerMustBeActive() {
		String id = planned(bia, "Estúdio", "2026-01-20");
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id = '%s'".formatted(biaMember));
		assertProblem(pay(bia, id, "{}"), HttpStatus.UNPROCESSABLE_CONTENT, "MEMBER_INACTIVE");

		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET user_id = NULL WHERE id = '%s'".formatted(biaMember));
		assertProblem(pay(bia, id, "{}"), HttpStatus.UNPROCESSABLE_CONTENT, "PAYER_REQUIRED");
	}

	@Test
	void onlyAdminsRevertAPaymentAndItIsAudited() {
		String id = planned(bia, "Estúdio", "2026-01-20");
		pay(bia, id, "{}");

		assertProblem(revert(bia, id), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		assertThat(JsonPath.<Boolean>read(get(admin, id), "$.access.revertPayment")).isTrue();
		ResponseEntity<String> reverted = revert(admin, id);

		assertThat(JsonPath.<String>read(reverted.getBody(), "$.status")).isEqualTo("PLANNED");
		assertThat(JsonPath.<Object>read(reverted.getBody(), "$.paidByMemberId")).isNull();
		assertThat(JsonPath.<Object>read(reverted.getBody(), "$.paidOn")).isNull();
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'expense.payment_reverted'"))
			.contains(biaMember.toString());
	}

	@Test
	void upcomingListsPlannedExpensesOverdueFirstUpToSomeDaysAhead() {
		planned(bia, "Atrasado", "2026-01-10");
		planned(bia, "Hoje", "2026-01-15");
		planned(bia, "Semana que vem", "2026-01-22");
		planned(bia, "Mês que vem", "2026-02-20");
		String paid = planned(bia, "Já pago", "2026-01-16");
		pay(bia, paid, "{}");

		ResponseEntity<String> week = band.get("/api/expenses/upcoming?days=7", headers -> headers.setBearerAuth(caio));
		assertThat(JsonPath.<List<String>>read(week.getBody(), "$[*].description")).containsExactly("Atrasado", "Hoje",
				"Semana que vem");
		assertThat(JsonPath.<List<Boolean>>read(week.getBody(), "$[*].overdue")).containsExactly(true, false, false);
		assertThat(JsonPath.<List<String>>read(band.get("/api/expenses/upcoming", headers -> headers.setBearerAuth(caio))
			.getBody(), "$[*].description")).containsExactly("Atrasado", "Hoje", "Semana que vem");
		assertThat(band.get("/api/expenses/upcoming?days=400", headers -> headers.setBearerAuth(caio)).getStatusCode())
			.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void overdueFollowsTheBandsTimeZone() {
		String id = planned(bia, "Hoje", "2026-01-15");
		// 01:00 UTC on the 16th is still 22:00 on the 15th in São Paulo.
		TestClockConfiguration.CLOCK.advance(Duration.ofHours(10));
		bia = fixtures.login(band, "bia@example.com");
		admin = fixtures.login(band, "ana@example.com");

		assertThat(JsonPath.<Boolean>read(get(bia, id), "$.overdue")).isFalse();
		band.patchJson("/api/band", "{\"timezone\": \"Europe/Lisbon\"}", headers -> headers.setBearerAuth(admin));
		assertThat(JsonPath.<Boolean>read(get(bia, id), "$.overdue")).isTrue();
	}

	private String planned(String token, String description, String date) {
		ResponseEntity<String> created = band.postJson("/api/expenses", """
				{"description": "%s", "categoryId": "%s", "currency": "BRL", "amountCents": 30000,
				 "expenseDate": "%s", "status": "PLANNED"}""".formatted(description, gear, date),
				headers -> headers.setBearerAuth(token));
		assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
		return JsonPath.read(created.getBody(), "$.id");
	}

	private ResponseEntity<String> pay(String token, String id, String json) {
		return band.postJson("/api/expenses/" + id + "/pay", json, headers -> headers.setBearerAuth(token));
	}

	private ResponseEntity<String> revert(String token, String id) {
		return band.postJson("/api/expenses/" + id + "/revert-payment", "{}", headers -> headers.setBearerAuth(token));
	}

	private String get(String token, String id) {
		return band.get("/api/expenses/" + id, headers -> headers.setBearerAuth(token)).getBody();
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

package com.burstintofire.bandcore.finance.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

/** The mockup: the vocalist paid R$ 2.700,00 split among the four members. */
class BalancesIT extends IntegrationTest {

	UUID alpha;

	UUID vocalist;

	UUID guitar;

	UUID bass;

	UUID drums;

	UUID category;

	ApiClient band;

	String token;

	@BeforeEach
	void bandWithFourMembers() {
		alpha = fixtures.band("alpha");
		UUID user = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, user, "ADMIN");
		vocalist = fixtures.memberOf(alpha, user);
		guitar = fixtures.member(alpha, "Bia");
		bass = fixtures.member(alpha, "Caio");
		drums = fixtures.member(alpha, "Dani");
		category = fixtures.category(alpha, "Estúdio");
		band = api.forBand("alpha");
		token = fixtures.login(band, "ana@example.com");
	}

	@Test
	void theMockupScenarioAddsUpToZero() {
		expense(270_000, "PAID", "BRL", "2026-11-05");
		// Planned expenses do not move balances yet.
		expense(80_000, "PLANNED", "BRL", "2026-11-25");

		String body = balances("");

		assertThat(JsonPath.<List<String>>read(body, "$.currencies[*].currency")).containsExactly("BRL");
		assertThat(JsonPath.<String>read(body, "$.currencies[0].members[0].memberId")).isEqualTo(vocalist.toString());
		assertThat(JsonPath.<List<Integer>>read(body, "$.currencies[0].members[*].balanceCents"))
			.containsExactly(202_500, -67_500, -67_500, -67_500);
		assertThat(JsonPath.<List<String>>read(body, "$.currencies[0].members[*].direction"))
			.containsExactly("RECEIVES", "OWES", "OWES", "OWES");
		assertThat(JsonPath.<Integer>read(body, "$.currencies[0].members[0].paidCents")).isEqualTo(270_000);
		assertThat(JsonPath.<Integer>read(body, "$.currencies[0].members[0].shareCents")).isEqualTo(67_500);
	}

	@Test
	void deletedExpensesLeaveTheBalancesAndCurrenciesStaySeparate() {
		String deleted = expense(10_000, "PAID", "BRL", "2026-11-05");
		band.send(HttpMethod.DELETE, "/api/expenses/" + deleted, null, headers -> headers.setBearerAuth(token));
		expense(4_000, "PAID", "USD", "2026-11-05");

		String body = balances("");

		assertThat(JsonPath.<List<String>>read(body, "$.currencies[*].currency")).containsExactly("USD");
		assertThat(JsonPath.<List<Integer>>read(body, "$.currencies[0].members[*].balanceCents"))
			.containsExactly(3_000, -1_000, -1_000, -1_000);
	}

	@Test
	void asOfCountsPaymentsUpToThatDay() {
		expense(40_000, "PAID", "BRL", "2026-10-31");
		expense(400, "PAID", "BRL", "2026-11-01");

		assertThat(JsonPath.<Integer>read(balances("?asOf=2026-10-31"), "$.currencies[0].members[0].balanceCents"))
			.isEqualTo(30_000);
		assertThat(JsonPath.<Integer>read(balances(""), "$.currencies[0].members[0].balanceCents")).isEqualTo(30_300);
		assertThat(JsonPath.<String>read(balances("?asOf=2026-10-31"), "$.asOf")).isEqualTo("2026-10-31");
	}

	@Test
	void membersWhoLeftAppearOnlyWhileTheyHaveABalance() {
		UUID left = fixtures.member(alpha, "Edu");
		expense(10_000, "PAID", "BRL", "2026-11-05");
		OwnerSql.inBand(alpha, "UPDATE bandcore.members SET status = 'INACTIVE' WHERE id IN ('%s', '%s')"
			.formatted(left, drums));

		String body = balances("");

		// Edu and Dani shared the expense and left: they still owe, so they are listed as inactive.
		assertThat(JsonPath.<List<String>>read(body, "$.currencies[0].members[*].memberId")).hasSize(5)
			.contains(left.toString(), drums.toString());
		assertThat(JsonPath.<List<Boolean>>read(body, "$.currencies[0].members[?(@.active == false)].active"))
			.hasSize(2);
	}

	@Test
	void balancesOnlyIncludeTheCurrentBand() {
		UUID other = fixtures.band("other");
		fixtures.membership(other, fixtures.verifiedUser("bruno@example.com"), "ADMIN");
		fixtures.paidExpense(other, fixtures.category(other, "X"), "Other band", UUID.randomUUID());

		ResponseEntity<String> response = band.get("/api/balances", headers -> headers.setBearerAuth(token));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<List<Object>>read(response.getBody(), "$.currencies")).isEmpty();
	}

	private String expense(long cents, String status, String currency, String date) {
		ResponseEntity<String> created = band.postJson("/api/expenses", """
				{"description": "Estúdio", "categoryId": "%s", "currency": "%s", "amountCents": %d,
				 "expenseDate": "%s", "status": "%s"%s}""".formatted(category, currency, cents, date, status,
				status.equals("PAID") ? ", \"paidByMemberId\": \"%s\"".formatted(vocalist) : ""),
				headers -> headers.setBearerAuth(token));
		assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
		return JsonPath.read(created.getBody(), "$.id");
	}

	private String balances(String query) {
		ResponseEntity<String> response = band.get("/api/balances" + query, headers -> headers.setBearerAuth(token));
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

}

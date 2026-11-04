package com.burstintofire.bandcore.finance.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import com.burstintofire.bandcore.support.TestClockConfiguration;

/** The mockup's month: R$ 2.700,00 paid and R$ 800,00 planned, R$ 3.500,00 in total. */
class FinanceSummaryIT extends IntegrationTest {

	UUID vocalist;

	UUID rehearsal;

	UUID photo;

	ApiClient band;

	String token;

	@BeforeEach
	void bandWithAnAdmin() {
		UUID alpha = fixtures.band("alpha");
		UUID user = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, user, "ADMIN");
		vocalist = fixtures.memberOf(alpha, user);
		rehearsal = fixtures.category(alpha, "Estúdio");
		photo = fixtures.category(alpha, "Fotografia");
		band = api.forBand("alpha");
		token = fixtures.login(band, "ana@example.com");
	}

	@Test
	void theMockupMonth() {
		expense("Estúdio", rehearsal, 120_000, "PAID", "BRL", "2026-11-03");
		expense("Van", rehearsal, 150_000, "PAID", "BRL", "2026-11-12");
		expense("Fotógrafo", photo, 80_000, "PLANNED", "BRL", "2026-11-25");
		// Other months and other currencies stay out of the BRL numbers.
		expense("Outubro", rehearsal, 9_900, "PAID", "BRL", "2026-10-31");
		expense("Pedal", photo, 2_500, "PAID", "USD", "2026-11-04");

		String body = summary("?month=2026-11");

		assertThat(JsonPath.<String>read(body, "$.month")).isEqualTo("2026-11");
		assertThat(JsonPath.<List<String>>read(body, "$.currencies[*].currency")).containsExactly("BRL", "USD");
		assertThat(JsonPath.<Integer>read(body, "$.currencies[0].totalCents")).isEqualTo(350_000);
		assertThat(JsonPath.<Integer>read(body, "$.currencies[0].paidCents")).isEqualTo(270_000);
		assertThat(JsonPath.<Integer>read(body, "$.currencies[0].plannedCents")).isEqualTo(80_000);
		assertThat(JsonPath.<List<String>>read(body, "$.currencies[0].categories[*].categoryId"))
			.containsExactly(rehearsal.toString(), photo.toString());
		assertThat(JsonPath.<List<Integer>>read(body, "$.currencies[0].categories[*].totalCents"))
			.containsExactly(270_000, 80_000);
		assertThat(JsonPath.<Integer>read(body, "$.currencies[1].totalCents")).isEqualTo(2_500);
		assertThat(JsonPath.<List<String>>read(body, "$.latest[*].description")).containsExactly("Fotógrafo", "Van",
				"Pedal", "Estúdio");
	}

	@Test
	void upcomingListsPlannedExpensesOfTheNextThirtyDays() {
		// The test clock says 2026-01-15.
		expense("Atrasado", photo, 100, "PLANNED", "BRL", "2026-01-10");
		expense("Logo", photo, 200, "PLANNED", "BRL", "2026-02-10");
		expense("Depois", photo, 300, "PLANNED", "BRL", "2026-03-10");

		String body = summary("?month=2026-01");

		assertThat(JsonPath.<List<String>>read(body, "$.upcoming[*].description")).containsExactly("Atrasado", "Logo");
		assertThat(JsonPath.<List<Boolean>>read(body, "$.upcoming[*].overdue")).containsExactly(true, false);
	}

	@Test
	void withoutAMonthItIsTheCurrentMonthInTheBandsTimeZone() {
		// 01:00 UTC on November 1st is still October 31st in São Paulo.
		TestClockConfiguration.CLOCK.advance(
				Duration.between(TestClockConfiguration.NOW, Instant.parse("2026-11-01T01:00:00Z")));
		token = fixtures.login(band, "ana@example.com");
		expense("Outubro", rehearsal, 1_000, "PAID", "BRL", "2026-10-31");

		String body = summary("");

		assertThat(JsonPath.<String>read(body, "$.month")).isEqualTo("2026-10");
		assertThat(JsonPath.<Integer>read(body, "$.currencies[0].totalCents")).isEqualTo(1_000);
	}

	@Test
	void anEmptyMonthHasNoCurrencies() {
		String body = summary("?month=2026-11");

		assertThat(JsonPath.<List<Object>>read(body, "$.currencies")).isEmpty();
		assertThat(JsonPath.<List<Object>>read(body, "$.latest")).isEmpty();
		assertThat(band.get("/api/summary/finance?month=2026-13", headers -> headers.setBearerAuth(token))
			.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private void expense(String description, UUID category, long cents, String status, String currency, String date) {
		ResponseEntity<String> created = band.postJson("/api/expenses", """
				{"description": "%s", "categoryId": "%s", "currency": "%s", "amountCents": %d,
				 "expenseDate": "%s", "status": "%s"%s}""".formatted(description, category, currency, cents, date,
				status, status.equals("PAID") ? ", \"paidByMemberId\": \"%s\"".formatted(vocalist) : ""),
				headers -> headers.setBearerAuth(token));
		assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
	}

	private String summary(String query) {
		ResponseEntity<String> response = band.get("/api/summary/finance" + query,
				headers -> headers.setBearerAuth(token));
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

}

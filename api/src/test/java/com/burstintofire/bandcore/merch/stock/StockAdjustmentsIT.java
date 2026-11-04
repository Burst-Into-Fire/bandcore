package com.burstintofire.bandcore.merch.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class StockAdjustmentsIT extends IntegrationTest {

	@Autowired
	StockService stock;

	@Autowired
	TransactionTemplate transactions;

	UUID alpha;

	UUID medium;

	ApiClient band;

	String admin;

	@BeforeEach
	void twelveShirtsAtTwentyFiveReais() {
		alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		fixtures.membership(alpha, fixtures.verifiedUser("bia@example.com"), "MEMBER");
		fixtures.membership(alpha, fixtures.verifiedUser("rui@example.com"), "READ_ONLY");
		medium = fixtures.variant(alpha, fixtures.product(alpha, "Camiseta preta"), "M");
		fixtures.batch(alpha, medium, 12, 2500, "Gráfica");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
	}

	@AfterEach
	void theLedgerStillExplainsTheStock() {
		List<StockService.Drift> drift = TenantContextHolder.callAs(new TenantContext(alpha, "alpha"),
				() -> transactions.execute(status -> stock.drift()));
		assertThat(drift).isEmpty();
	}

	@Test
	void aCountSetsTheExactQuantityAndKeepsTheAverageCost() {
		ResponseEntity<String> counted = adjust(admin, """
				{"countedQuantity": 10, "reason": "COUNT", "note": " Contagem do show "}""");

		assertThat(counted.getStatusCode()).as(counted.getBody()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<String>read(counted.getBody(), "$.type")).isEqualTo("ADJUSTMENT");
		assertThat(JsonPath.<Integer>read(counted.getBody(), "$.quantityDelta")).isEqualTo(-2);
		assertThat(JsonPath.<Integer>read(counted.getBody(), "$.quantityAfter")).isEqualTo(10);
		assertThat(JsonPath.<String>read(counted.getBody(), "$.reason")).isEqualTo("COUNT");
		assertThat(JsonPath.<String>read(counted.getBody(), "$.note")).isEqualTo("Contagem do show");
		assertThat(quantity()).isEqualTo("10");
		assertThat(average()).isEqualTo("2500");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'stock.adjusted'"))
			.contains("\"reason\": \"COUNT\"")
			.contains("\"quantity_delta\": -2");

		// Units found again come in at the same average.
		assertThat(adjust(admin, """
				{"quantityDelta": 3, "reason": "OTHER", "note": "Achamos no case"}""").getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
		assertThat(quantity()).isEqualTo("13");
		assertThat(average()).isEqualTo("2500");
	}

	@Test
	void stockNeverGoesBelowZero() {
		ResponseEntity<String> tooMany = adjust(admin, """
				{"quantityDelta": -13, "reason": "LOST"}""");

		assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(tooMany.getBody(), "$.code")).isEqualTo("INSUFFICIENT_STOCK");
		assertThat(JsonPath.<Integer>read(tooMany.getBody(), "$.available")).isEqualTo(12);
		assertThat(adjust(admin, """
				{"countedQuantity": -1, "reason": "COUNT"}""").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(adjust(admin, """
				{"quantityDelta": -12, "reason": "DAMAGED"}""").getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(quantity()).isEqualTo("0");
	}

	@Test
	void anAdjustmentNeedsAChangeAndAReason() {
		assertProblem(adjust(admin, """
				{"countedQuantity": 12, "reason": "COUNT"}"""), HttpStatus.UNPROCESSABLE_CONTENT, "STOCK_UNCHANGED");
		assertProblem(adjust(admin, """
				{"quantityDelta": 0, "reason": "GIFT"}"""), HttpStatus.UNPROCESSABLE_CONTENT, "STOCK_UNCHANGED");
		assertProblem(adjust(admin, """
				{"countedQuantity": 10, "quantityDelta": -2, "reason": "COUNT"}"""), HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED");
		assertProblem(adjust(admin, """
				{"reason": "COUNT"}"""), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		ResponseEntity<String> other = adjust(admin, """
				{"quantityDelta": -1, "reason": "OTHER", "note": " "}""");
		assertProblem(other, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertThat(JsonPath.<String>read(other.getBody(), "$.errors[0].field")).isEqualTo("note");
		assertProblem(adjust(admin, """
				{"quantityDelta": -1}"""), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertThat(quantity()).isEqualTo("12");
	}

	@Test
	void archivedVariantsCanBeWrittenOff() {
		OwnerSql.inBand(alpha, "UPDATE bandcore.product_variants SET archived_at = now() WHERE id = '%s'".formatted(medium));

		assertThat(adjust(admin, """
				{"countedQuantity": 0, "reason": "GIFT"}""").getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(quantity()).isEqualTo("0");
	}

	@Test
	void onlyAdminsAdjustAndOtherBandsVariantsAreNotFound() {
		String member = fixtures.login(band, "bia@example.com");
		String reader = fixtures.login(band, "rui@example.com");
		String body = """
				{"quantityDelta": -1, "reason": "LOST"}""";

		assertProblem(adjust(member, body), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		assertProblem(adjust(reader, body), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		UUID bravo = fixtures.band("bravo");
		UUID theirs = fixtures.variant(bravo, fixtures.product(bravo, "Boné"), "U");
		fixtures.received(bravo, theirs, 5);
		assertThat(band.postJson("/api/variants/" + theirs + "/adjustments", """
				{"countedQuantity": 0, "reason": "COUNT"}""", headers -> headers.setBearerAuth(admin)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT quantity_on_hand FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(theirs)))
			.isEqualTo("5");
		assertThat(quantity()).isEqualTo("12");
	}

	private ResponseEntity<String> adjust(String token, String json) {
		return band.postJson("/api/variants/" + medium + "/adjustments", json, headers -> headers.setBearerAuth(token));
	}

	private String quantity() {
		return OwnerSql.superuserQueryForString(
				"SELECT quantity_on_hand FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(medium));
	}

	private String average() {
		return OwnerSql.superuserQueryForString(
				"SELECT average_unit_cost_cents FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(medium));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

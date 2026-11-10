package com.burstintofire.bandcore.merch.sale;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class SalesIT extends IntegrationTest {

	UUID alpha;

	UUID ana;

	UUID event;

	UUID shirt;

	UUID medium;

	UUID large;

	ApiClient band;

	String admin;

	String readOnly;

	@BeforeEach
	void bandWithStockAndAnOpenEvent() {
		alpha = fixtures.band("alpha");
		ana = fixtures.verifiedUser("ana@example.com");
		fixtures.membership(alpha, ana, "ADMIN");
		fixtures.membership(alpha, fixtures.verifiedUser("reader@example.com"), "READ_ONLY");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		readOnly = fixtures.login(band, "reader@example.com");
		event = fixtures.event(alpha, "Festival", Instant.parse("2026-02-01T20:00:00Z"));
		shirt = fixtures.product(alpha, "Camiseta preta");
		medium = fixtures.variant(alpha, shirt, "M");
		large = fixtures.variant(alpha, shirt, "G");
		fixtures.batch(alpha, medium, 10, 2500, "Estamparia A");
		fixtures.batch(alpha, large, 5, 3500, "Estamparia B");
	}

	@Test
	void saleAndStockMovementsCommitTogetherAndRetriesAreIdempotent() {
		String id = UUID.randomUUID().toString();
		String request = """
				{"id": "%s", "eventId": "%s", "paymentMethod": "PIX",
				 "items": [{"variantId": "%s", "quantity": 2, "discountCents": 500,
				            "discountReason": "Promoção"},
				           {"variantId": "%s", "quantity": 1}]}"""
			.formatted(id, event, medium, large);

		ResponseEntity<String> created = post(request);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<String>read(created.getBody(), "$.id")).isEqualTo(id);
		assertThat(JsonPath.<String>read(created.getBody(), "$.eventId")).isEqualTo(event.toString());
		assertThat(JsonPath.<String>read(created.getBody(), "$.sellerUserId")).isEqualTo(ana.toString());
		assertThat(JsonPath.<String>read(created.getBody(), "$.currency")).isEqualTo("BRL");
		assertThat(JsonPath.<Integer>read(created.getBody(), "$.totalCents")).isEqualTo(17500);
		assertThat(JsonPath.<String>read(created.getBody(), "$.status")).isEqualTo("COMPLETED");
		assertThat(JsonPath.<String>read(created.getBody(), "$.source")).isEqualTo("ONLINE");
		assertThat(JsonPath.<List<Integer>>read(created.getBody(), "$.items[*].unitPriceCents"))
			.containsExactly(6000, 6000);
		assertThat(JsonPath.<List<Integer>>read(created.getBody(), "$.items[*].unitCostCents"))
			.containsExactly(2500, 3500);
		assertThat(JsonPath.<Integer>read(created.getBody(), "$.items[0].discountCents")).isEqualTo(500);
		assertThat(quantity(medium)).isEqualTo(8);
		assertThat(quantity(large)).isEqualTo(4);
		assertThat(OwnerSql.superuserQueryForString("""
				SELECT count(*) FROM bandcore.stock_movements
				WHERE source_type = 'SALE' AND source_id = '%s'
				""".formatted(id))).isEqualTo("2");
		assertThat(OwnerSql.superuserQueryForString("""
				SELECT count(*) FROM bandcore.sale_items i
				JOIN bandcore.sales s ON (s.band_id, s.id) = (i.band_id, i.sale_id)
				WHERE s.id = '%s'
				""".formatted(id))).isEqualTo("2");

		ResponseEntity<String> replay = post(request);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(replay.getBody(), "$.id")).isEqualTo(id);
		assertThat(quantity(medium)).isEqualTo(8);
		assertThat(quantity(large)).isEqualTo(4);
		assertThat(auditActions()).containsExactly("sale.created");

		String changedPayload = request.replace("\"PIX\"", "\"CASH\"");
		assertProblem(post(changedPayload), HttpStatus.CONFLICT, "SALE_IDEMPOTENCY_CONFLICT");
	}

	@Test
	void anItemShortOnStockRollsBackTheEntireSale() {
		String request = """
				{"id": "%s", "paymentMethod": "CASH",
				 "items": [{"variantId": "%s", "quantity": 2}, {"variantId": "%s", "quantity": 6}]}"""
			.formatted(UUID.randomUUID(), medium, large);

		ResponseEntity<String> refused = post(request);

		assertProblem(refused, HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");
		assertThat(JsonPath.<String>read(refused.getBody(), "$.variantId")).isEqualTo(large.toString());
		assertThat(JsonPath.<Integer>read(refused.getBody(), "$.available")).isEqualTo(5);
		assertThat(quantity(medium)).isEqualTo(10);
		assertThat(quantity(large)).isEqualTo(5);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.sales")).isEqualTo("0");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.stock_movements WHERE type = 'SALE'"))
			.isEqualTo("0");
	}

	@Test
	void closedEventsArchivedVariantsAndMixedCurrenciesAreRejected() {
		OwnerSql.inBand(alpha, """
				UPDATE bandcore.events SET status = 'CLOSED', closed_at = now(), closed_by = '%s' WHERE id = '%s'
				""".formatted(ana, event));
		assertProblem(post(oneItem(UUID.randomUUID(), event, medium)), HttpStatus.CONFLICT, "EVENT_CLOSED");

		OwnerSql.inBand(alpha, """
				UPDATE bandcore.events SET status = 'OPEN', closed_at = NULL, closed_by = NULL WHERE id = '%s'
				""".formatted(event));
		OwnerSql.inBand(alpha, "UPDATE bandcore.product_variants SET archived_at = now() WHERE id = '%s'".formatted(medium));
		assertProblem(post(oneItem(UUID.randomUUID(), event, medium)), HttpStatus.UNPROCESSABLE_CONTENT,
				"VARIANT_ARCHIVED");
		OwnerSql.inBand(alpha, "UPDATE bandcore.product_variants SET archived_at = NULL WHERE id = '%s'".formatted(medium));

		UUID usdProduct = fixtures.product(alpha, "Boné");
		UUID usdVariant = fixtures.variant(alpha, usdProduct, "Único");
		OwnerSql.inBand(alpha, "UPDATE bandcore.products SET currency = 'USD' WHERE id = '%s'".formatted(usdProduct));
		fixtures.received(alpha, usdVariant, 5);
		String mixedCurrency = """
				{"id": "%s", "paymentMethod": "CARD",
				 "items": [{"variantId": "%s", "quantity": 1}, {"variantId": "%s", "quantity": 1}]}"""
			.formatted(UUID.randomUUID(), large, usdVariant);
		assertProblem(post(mixedCurrency), HttpStatus.UNPROCESSABLE_CONTENT, "SALE_CURRENCY_MISMATCH");
		assertThat(quantity(large)).isEqualTo(5);
		assertThat(quantity(usdVariant)).isEqualTo(5);
	}

	@Test
	void validationAndCrossBandVariantsAreRejected() {
		assertProblem(post("""
				{"id": "%s", "paymentMethod": "CASH",
				 "items": [{"variantId": "%s", "quantity": 0}]}""".formatted(UUID.randomUUID(), medium)),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(post("""
				{"id": "%s", "paymentMethod": "CASH",
				 "items": [{"variantId": "%s", "quantity": 1, "discountCents": 100}]}"""
					.formatted(UUID.randomUUID(), medium)),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(post("""
				{"id": "%s", "paymentMethod": "CASH",
				 "items": [{"variantId": "%s", "quantity": 1}, {"variantId": "%s", "quantity": 1}]}"""
					.formatted(UUID.randomUUID(), medium, medium)),
				HttpStatus.UNPROCESSABLE_CONTENT, "SALE_DUPLICATE_VARIANT");

		UUID bravo = fixtures.band("bravo");
		fixtures.membership(bravo, fixtures.verifiedUser("bia@example.com"), "ADMIN");
		UUID product = fixtures.product(bravo, "Camisa");
		UUID foreignVariant = fixtures.variant(bravo, product, "M");
		fixtures.received(bravo, foreignVariant, 1);
		assertProblem(post(oneItem(UUID.randomUUID(), null, foreignVariant)), HttpStatus.NOT_FOUND, "NOT_FOUND");
		assertThat(quantity(medium)).isEqualTo(10);
	}

	@Test
	void readOnlyMembersCannotRegisterSales() {
		assertProblem(band.postJson("/api/sales", oneItem(UUID.randomUUID(), null, medium),
				headers -> headers.setBearerAuth(readOnly)), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		assertThat(quantity(medium)).isEqualTo(10);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.sales")).isEqualTo("0");
	}

	@Test
	void concurrentSalesCannotSellTheLastUnitTwice() throws Exception {
		UUID product = fixtures.product(alpha, "Patch exclusivo");
		UUID lastUnit = fixtures.variant(alpha, product, "U");
		fixtures.received(alpha, lastUnit, 1);
		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ResponseEntity<String>>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
			for (int index = 0; index < requests; index++) {
				String payload = oneItem(UUID.randomUUID(), null, lastUnit);
				results.add(pool.submit(() -> {
					start.await();
					return post(payload);
				}));
			}
			start.countDown();
			int completed = 0;
			int insufficient = 0;
			for (Future<ResponseEntity<String>> result : results) {
				ResponseEntity<String> response = result.get();
				if (response.getStatusCode() == HttpStatus.CREATED) {
					completed++;
				}
				else {
					assertProblem(response, HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");
					insufficient++;
				}
			}
			assertThat(completed).isEqualTo(1);
			assertThat(insufficient).isEqualTo(requests - 1);
		}
		assertThat(quantity(lastUnit)).isZero();
		assertThat(OwnerSql.superuserQueryForString("""
				SELECT count(*) FROM bandcore.stock_movements
				WHERE variant_id = '%s' AND type = 'SALE'
				""".formatted(lastUnit))).isEqualTo("1");
		assertThat(OwnerSql.superuserQueryForString("""
				SELECT count(*) FROM bandcore.sales s JOIN bandcore.sale_items i
				  ON (i.band_id, i.sale_id) = (s.band_id, s.id)
				WHERE i.variant_id = '%s'
				""".formatted(lastUnit))).isEqualTo("1");
	}

	private ResponseEntity<String> post(String body) {
		return band.postJson("/api/sales", body, headers -> headers.setBearerAuth(admin));
	}

	private static String oneItem(UUID id, UUID event, UUID variant) {
		return """
				{"id": "%s", "eventId": %s, "paymentMethod": "PIX",
				 "items": [{"variantId": "%s", "quantity": 1}]}"""
			.formatted(id, event == null ? "null" : "\"" + event + "\"", variant);
	}

	private int quantity(UUID variant) {
		return Integer.valueOf(OwnerSql.superuserQueryForString("""
				SELECT quantity_on_hand FROM bandcore.stock_levels WHERE band_id = '%s' AND variant_id = '%s'
				""".formatted(alpha, variant)));
	}

	private List<String> auditActions() {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY action) FROM bandcore.audit_events WHERE action LIKE 'sale.%'");
		return actions == null ? List.of() : List.of(actions.split(","));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

}

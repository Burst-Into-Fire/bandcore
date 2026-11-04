package com.burstintofire.bandcore.merch.batch;

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

import com.burstintofire.bandcore.merch.stock.StockService;
import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class BatchesIT extends IntegrationTest {

	@Autowired
	StockService stock;

	@Autowired
	TransactionTemplate transactions;

	UUID alpha;

	UUID shirt;

	UUID medium;

	UUID large;

	ApiClient band;

	String admin;

	String member;

	@BeforeEach
	void aShirtInTwoSizes() {
		alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		fixtures.membership(alpha, fixtures.verifiedUser("bia@example.com"), "MEMBER");
		shirt = fixtures.product(alpha, "Camiseta preta");
		medium = fixtures.variant(alpha, shirt, "M");
		large = fixtures.variant(alpha, shirt, "G");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		member = fixtures.login(band, "bia@example.com");
	}

	@AfterEach
	void theLedgerStillExplainsTheStock() {
		List<StockService.Drift> drift = TenantContextHolder.callAs(new TenantContext(alpha, "alpha"),
				() -> transactions.execute(status -> stock.drift()));
		assertThat(drift).isEmpty();
	}

	@Test
	void aBatchBringsStockInAtItsCost() {
		ResponseEntity<String> registered = register("""
				{"supplier": "Gráfica Rock", "orderedOn": "2026-01-02", "receivedOn": "2026-01-10", "currency": "BRL",
				 "costMode": "PER_ITEM", "notes": "Serigrafia",
				 "items": [{"variantId": "%s", "quantity": 10, "unitCostCents": 3000},
				           {"variantId": "%s", "quantity": 5, "unitCostCents": 3200}]}""".formatted(medium, large));

		assertThat(registered.getStatusCode()).as(registered.getBody()).isEqualTo(HttpStatus.CREATED);
		String id = JsonPath.read(registered.getBody(), "$.id");
		assertThat(registered.getHeaders().getLocation()).hasToString("/api/batches/" + id);
		assertThat(JsonPath.<Integer>read(registered.getBody(), "$.totalCostCents")).isEqualTo(46_000);
		assertThat(JsonPath.<List<Integer>>read(registered.getBody(), "$.items[*].lineCostCents"))
			.containsExactly(30_000, 16_000);
		assertThat(JsonPath.<List<String>>read(registered.getBody(), "$.items[*].productName"))
			.containsExactly("Camiseta preta", "Camiseta preta");
		assertThat(JsonPath.<String>read(registered.getBody(), "$.status")).isEqualTo("RECEIVED");
		assertThat(JsonPath.<Boolean>read(registered.getBody(), "$.cancellable")).isTrue();

		String catalog = band.get("/api/products/" + shirt, headers -> headers.setBearerAuth(member)).getBody();
		assertThat(JsonPath.<List<Integer>>read(catalog, "$.variants[*].quantityOnHand")).containsExactly(10, 5);
		assertThat(JsonPath.<List<Integer>>read(catalog, "$.variants[*].averageUnitCostCents"))
			.containsExactly(3000, 3200);
		String history = band.get("/api/variants/" + medium + "/movements", headers -> headers.setBearerAuth(member))
			.getBody();
		assertThat(JsonPath.<List<String>>read(history, "$.items[*].type")).containsExactly("BATCH_IN");
		assertThat(JsonPath.<List<String>>read(history, "$.items[*].sourceId")).containsExactly(id);
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes ->> 'total_cost_cents' FROM bandcore.audit_events WHERE action = 'production_batch.registered'"))
			.isEqualTo("46000");
	}

	@Test
	void aSecondBatchAtAnotherCostMovesTheAverageToTheCent() {
		register(perItem(medium, 100, 3000));
		register(perItem(medium, 50, 3500));

		// R$ 4.750,00 for 150 shirts: R$ 31,666... rounds half-up to R$ 31,67.
		assertThat(averageOf(medium)).isEqualTo("3167");
		assertThat(quantityOf(medium)).isEqualTo("150");
	}

	@Test
	void anAllocatedTotalIsSplitByQuantityAndAddsUpExactly() {
		UUID patch = fixtures.variant(alpha, fixtures.product(alpha, "Patch"), "U");

		ResponseEntity<String> registered = register("""
				{"currency": "BRL", "costMode": "ALLOCATED", "totalCostCents": 10000,
				 "items": [{"variantId": "%s", "quantity": 1}, {"variantId": "%s", "quantity": 1},
				           {"variantId": "%s", "quantity": 1}]}""".formatted(medium, large, patch));

		assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		List<Integer> lines = JsonPath.read(registered.getBody(), "$.items[*].lineCostCents");
		assertThat(lines).containsExactly(3334, 3333, 3333);
		assertThat(lines.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10_000);
		assertThat(averageOf(medium)).isEqualTo("3334");
		assertThat(averageOf(patch)).isEqualTo("3333");
	}

	@Test
	void cancellingTheLatestBatchRestoresQuantityAndAverageExactly() {
		String first = JsonPath.read(register(perItem(medium, 10, 3000)).getBody(), "$.id");
		String second = JsonPath.read(register(perItem(medium, 5, 3600)).getBody(), "$.id");
		assertThat(averageOf(medium)).isEqualTo("3200");

		// The first batch is no longer the latest movement of M.
		ResponseEntity<String> older = cancel(first, 0);
		assertThat(older.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(older.getBody(), "$.code")).isEqualTo("BATCH_HAS_LATER_MOVEMENTS");
		String listed = band.get("/api/batches", headers -> headers.setBearerAuth(member)).getBody();
		assertThat(JsonPath.<List<Boolean>>read(listed, "$.items[?(@.id == '%s')].cancellable".formatted(first)))
			.containsExactly(false);
		assertThat(JsonPath.<List<Boolean>>read(listed, "$.items[?(@.id == '%s')].cancellable".formatted(second)))
			.containsExactly(true);

		ResponseEntity<String> cancelled = cancel(second, 0);

		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(cancelled.getBody(), "$.status")).isEqualTo("CANCELLED");
		assertThat(JsonPath.<Boolean>read(cancelled.getBody(), "$.cancellable")).isFalse();
		assertThat(quantityOf(medium)).isEqualTo("10");
		assertThat(averageOf(medium)).isEqualTo("3000");
		String history = band.get("/api/variants/" + medium + "/movements", headers -> headers.setBearerAuth(member))
			.getBody();
		assertThat(JsonPath.<List<String>>read(history, "$.items[*].type"))
			.containsExactly("BATCH_REVERSAL", "BATCH_IN", "BATCH_IN");
		assertThat(JsonPath.<String>read(cancel(second, 1).getBody(), "$.code")).isEqualTo("BATCH_CANCELLED");
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT count(*) FROM bandcore.audit_events WHERE action = 'production_batch.cancelled'"))
			.isEqualTo("1");
	}

	@Test
	void cancellingTheOnlyBatchLeavesNoAverage() {
		String id = JsonPath.read(register(perItem(medium, 10, 3000)).getBody(), "$.id");

		cancel(id, 0);

		assertThat(quantityOf(medium)).isEqualTo("0");
		assertThat(averageOf(medium)).isNull();
	}

	@Test
	void invalidBatchesAreRefusedAndNothingMoves() {
		assertProblem(register("""
				{"currency": "BRL", "costMode": "PER_ITEM",
				 "items": [{"variantId": "%s", "quantity": 1, "unitCostCents": 100},
				           {"variantId": "%s", "quantity": 2, "unitCostCents": 100}]}""".formatted(medium, medium)),
				HttpStatus.UNPROCESSABLE_CONTENT, "BATCH_DUPLICATE_VARIANT", "items[1].variantId");
		UUID cap = fixtures.variant(alpha, fixtures.product(alpha, "Boné"), "U");
		OwnerSql.inBand(alpha, "UPDATE bandcore.products SET currency = 'USD' WHERE name = 'Boné'");
		assertProblem(register(perItem(cap, 1, 100)), HttpStatus.UNPROCESSABLE_CONTENT, "BATCH_CURRENCY_MISMATCH",
				"items[0].variantId");
		OwnerSql.inBand(alpha, "UPDATE bandcore.product_variants SET archived_at = now() WHERE id = '%s'".formatted(large));
		assertProblem(register(perItem(large, 1, 100)), HttpStatus.UNPROCESSABLE_CONTENT, "VARIANT_ARCHIVED",
				"items[0].variantId");
		assertProblem(register("""
				{"currency": "BRL", "costMode": "PER_ITEM", "items": [{"variantId": "%s", "quantity": 1}]}"""
			.formatted(medium)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "items[0].unitCostCents");
		assertProblem(register("""
				{"currency": "BRL", "costMode": "ALLOCATED", "items": [{"variantId": "%s", "quantity": 1}]}"""
			.formatted(medium)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "totalCostCents");
		assertProblem(register("""
				{"currency": "BRL", "costMode": "PER_ITEM", "orderedOn": "2026-02-01", "receivedOn": "2026-01-10",
				 "items": [{"variantId": "%s", "quantity": 1, "unitCostCents": 100}]}""".formatted(medium)),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "orderedOn");
		assertThat(register("""
				{"currency": "BRL", "costMode": "PER_ITEM", "items": []}""").getStatusCode())
			.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(register(perItem(medium, -1, 100)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		UUID bravo = fixtures.band("bravo");
		UUID theirs = fixtures.variant(bravo, fixtures.product(bravo, "Boné"), "U");
		assertThat(register(perItem(theirs, 1, 100)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		ResponseEntity<String> byMember = band.postJson("/api/batches", perItem(medium, 1, 100),
				headers -> headers.setBearerAuth(member));
		assertThat(byMember.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.production_batches")).isEqualTo("0");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.stock_movements")).isEqualTo("0");
	}

	@Test
	void onlyTheDescriptionOfABatchChanges() {
		String id = JsonPath.read(register(perItem(medium, 10, 3000)).getBody(), "$.id");

		ResponseEntity<String> changed = band.patchJson("/api/batches/" + id, """
				{"version": 0, "supplier": "Estamparia Nova", "orderedOn": "2026-01-05", "notes": ""}""",
				headers -> headers.setBearerAuth(admin));

		assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(changed.getBody(), "$.supplier")).isEqualTo("Estamparia Nova");
		assertThat(JsonPath.<String>read(changed.getBody(), "$.orderedOn")).isEqualTo("2026-01-05");
		assertThat(JsonPath.<Integer>read(changed.getBody(), "$.totalCostCents")).isEqualTo(30_000);
		assertThat(JsonPath.<Integer>read(changed.getBody(), "$.version")).isEqualTo(1);
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'production_batch.updated'"))
			.contains("ordered_on");
		assertThat(band.patchJson("/api/batches/" + id, "{\"version\": 0, \"notes\": \"x\"}",
				headers -> headers.setBearerAuth(admin)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void batchesAreListedLatestReceivedFirst() {
		register(perItem(medium, 1, 100).replace("\"currency\"", "\"receivedOn\": \"2026-01-03\", \"currency\""));
		register(perItem(large, 1, 100).replace("\"currency\"", "\"receivedOn\": \"2026-01-09\", \"currency\""));

		String page = band.get("/api/batches?size=1", headers -> headers.setBearerAuth(member)).getBody();

		assertThat(JsonPath.<List<String>>read(page, "$.items[*].receivedOn")).containsExactly("2026-01-09");
		assertThat(JsonPath.<Integer>read(page, "$.totalItems")).isEqualTo(2);
		// Without a date, a batch is received today in the band's time zone (the test clock: 2026-01-15).
		String today = register(perItem(medium, 1, 100)).getBody();
		assertThat(JsonPath.<String>read(today, "$.receivedOn")).isEqualTo("2026-01-15");
	}

	private static String perItem(UUID variant, int quantity, long unitCost) {
		return """
				{"currency": "BRL", "costMode": "PER_ITEM",
				 "items": [{"variantId": "%s", "quantity": %d, "unitCostCents": %d}]}""".formatted(variant, quantity,
				unitCost);
	}

	private ResponseEntity<String> register(String json) {
		return band.postJson("/api/batches", json, headers -> headers.setBearerAuth(admin));
	}

	private ResponseEntity<String> cancel(String batchId, long version) {
		return band.postJson("/api/batches/" + batchId + "/cancel", "{\"version\": %d}".formatted(version),
				headers -> headers.setBearerAuth(admin));
	}

	private static String quantityOf(UUID variant) {
		return OwnerSql.superuserQueryForString(
				"SELECT quantity_on_hand FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(variant));
	}

	private static String averageOf(UUID variant) {
		return OwnerSql.superuserQueryForString(
				"SELECT average_unit_cost_cents FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(variant));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code, String field) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
		assertThat(JsonPath.<String>read(response.getBody(), "$.errors[0].field")).isEqualTo(field);
	}

}

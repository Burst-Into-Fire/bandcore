package com.burstintofire.bandcore.merch.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.burstintofire.bandcore.merch.stock.StockService.Change;
import com.burstintofire.bandcore.merch.stock.StockService.Source;
import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class StockServiceIT extends IntegrationTest {

	@Autowired
	StockService stock;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	UUID alpha;

	UUID shirt;

	UUID medium;

	UUID large;

	@BeforeEach
	void aShirtInTwoSizes() {
		alpha = fixtures.band("alpha");
		shirt = fixtures.product(alpha, "Camiseta preta");
		medium = fixtures.variant(alpha, shirt, "M");
		large = fixtures.variant(alpha, shirt, "G");
	}

	@Test
	void twentyPhonesSellingTheLastFiveUnitsSellExactlyFive() throws Exception {
		inBand(() -> stock.apply(MovementType.BATCH_IN, List.of(new Change(medium, 5)), Source.batch(UUID.randomUUID())));
		int phones = 20;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> sales = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(phones)) {
			for (int i = 0; i < phones; i++) {
				sales.add(pool.submit(() -> {
					start.await();
					try {
						inBand(() -> stock.apply(MovementType.SALE, List.of(new Change(medium, -1)),
								new Source("SALE", UUID.randomUUID())));
						return true;
					}
					catch (ApiException refused) {
						assertThat(refused.code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
						return false;
					}
				}));
			}
			start.countDown();
			int sold = 0;
			for (Future<Boolean> sale : sales) {
				sold += sale.get() ? 1 : 0;
			}
			assertThat(sold).isEqualTo(5);
		}

		assertThat(quantityOf(medium)).isZero();
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT count(*) FROM bandcore.stock_movements WHERE type = 'SALE' AND variant_id = '%s'".formatted(medium)))
			.isEqualTo("5");
		assertThat(inBand(stock::drift)).isEmpty();
	}

	@Test
	void aChangeBelowZeroChangesNothingAndSaysWhatIsAvailable() {
		inBand(() -> stock.apply(MovementType.BATCH_IN, List.of(new Change(medium, 3), new Change(large, 2)),
				Source.batch(UUID.randomUUID())));

		// All or nothing: M has enough, G does not, so neither moves.
		assertThatThrownBy(() -> inBand(() -> stock.apply(MovementType.SALE,
				List.of(new Change(medium, -1), new Change(large, -3)), new Source("SALE", UUID.randomUUID()))))
			.isInstanceOfSatisfying(ApiException.class, refused -> {
				assertThat(refused.status()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(refused.code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
				assertThat(refused.properties()).containsEntry("variantId", large).containsEntry("available", 2);
			});

		assertThat(quantityOf(medium)).isEqualTo(3);
		assertThat(quantityOf(large)).isEqualTo(2);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.stock_movements")).isEqualTo("2");
	}

	@Test
	void theMovementsAlwaysExplainTheQuantityOnHand() {
		inBand(() -> stock.apply(MovementType.BATCH_IN, List.of(new Change(medium, 10)), Source.batch(UUID.randomUUID())));
		inBand(() -> stock.apply(MovementType.SALE, List.of(new Change(medium, -4)), new Source("SALE", UUID.randomUUID())));
		inBand(() -> stock.apply(MovementType.REFUND, List.of(new Change(medium, 1)), new Source("REFUND", UUID.randomUUID())));
		assertThatThrownBy(() -> inBand(() -> stock.apply(MovementType.SALE, List.of(new Change(medium, -8)),
				new Source("SALE", UUID.randomUUID()))))
			.isInstanceOf(ApiException.class);
		inBand(() -> stock.apply(MovementType.BATCH_IN, List.of(new Change(large, 2)), Source.batch(UUID.randomUUID())));

		assertThat(quantityOf(medium)).isEqualTo(7);
		assertThat(inBand(stock::drift)).isEmpty();
		// Something that bypasses the service shows up as drift.
		OwnerSql.inBand(alpha,
				"UPDATE bandcore.stock_levels SET quantity_on_hand = 99 WHERE variant_id = '%s'".formatted(large));
		assertThat(inBand(stock::drift)).extracting(StockService.Drift::variantId).containsExactly(large);
	}

	@Test
	void theDatabaseRefusesANegativeQuantity() {
		assertThatThrownBy(() -> inBand(() -> jdbc.update(
				"UPDATE bandcore.stock_levels SET quantity_on_hand = -1 WHERE variant_id = ?", medium)))
			.rootCause()
			.hasMessageContaining("stock_levels_quantity_not_negative");
	}

	@ParameterizedTest
	@ValueSource(strings = { "UPDATE bandcore.stock_movements SET quantity_delta = 100",
			"DELETE FROM bandcore.stock_movements", "TRUNCATE bandcore.stock_movements" })
	void theApplicationCannotChangeOrDeleteMovements(String sql) {
		inBand(() -> stock.apply(MovementType.BATCH_IN, List.of(new Change(medium, 5)), Source.batch(UUID.randomUUID())));

		assertThatThrownBy(() -> inBand(() -> jdbc.update(sql))).rootCause().hasMessageContaining("permission denied");
	}

	@Test
	void anotherBandsVariantIsNotFound() {
		UUID bravo = fixtures.band("bravo");
		UUID theirs = fixtures.variant(bravo, fixtures.product(bravo, "Boné"), "U");

		assertThatThrownBy(() -> inBand(() -> stock.apply(MovementType.BATCH_IN, List.of(new Change(theirs, 1)),
				Source.batch(UUID.randomUUID()))))
			.isInstanceOfSatisfying(ApiException.class, refused -> assertThat(refused.status()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT quantity_on_hand FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(theirs)))
			.isEqualTo("0");
	}

	@Test
	void everyoneReadsTheStockAndItsHistory() {
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		fixtures.membership(alpha, fixtures.verifiedUser("rui@example.com"), "READ_ONLY");
		ApiClient band = api.forBand("alpha");
		String admin = fixtures.login(band, "ana@example.com");
		String reader = fixtures.login(band, "rui@example.com");
		OwnerSql.inBand(alpha, "UPDATE bandcore.product_variants SET low_stock_threshold = 3 WHERE id = '%s'".formatted(large));
		fixtures.received(alpha, medium, 12);
		fixtures.received(alpha, large, 5);
		inBand(() -> stock.apply(MovementType.SALE, List.of(new Change(large, -2)), new Source("SALE", UUID.randomUUID())));

		String catalog = band.get("/api/products", headers -> headers.setBearerAuth(reader)).getBody();
		assertThat(JsonPath.<List<Integer>>read(catalog, "$[0].variants[*].quantityOnHand")).containsExactly(12, 3);
		assertThat(JsonPath.<List<Boolean>>read(catalog, "$[0].variants[*].lowStock")).containsExactly(false, true);

		ResponseEntity<String> history = band.get("/api/variants/" + large + "/movements",
				headers -> headers.setBearerAuth(reader));
		assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<List<String>>read(history.getBody(), "$.items[*].type")).containsExactly("SALE", "BATCH_IN");
		assertThat(JsonPath.<List<Integer>>read(history.getBody(), "$.items[*].quantityAfter")).containsExactly(3, 5);
		assertThat(JsonPath.<Integer>read(history.getBody(), "$.totalItems")).isEqualTo(2);

		// A variant that moved stock is archived, not deleted; nor is its product, nor its currency changed.
		assertThat(band.send(HttpMethod.DELETE, "/api/variants/" + large, null, headers -> headers.setBearerAuth(admin))
			.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		ResponseEntity<String> product = band.send(HttpMethod.DELETE, "/api/products/" + shirt, null,
				headers -> headers.setBearerAuth(admin));
		assertThat(JsonPath.<String>read(product.getBody(), "$.code")).isEqualTo("PRODUCT_HAS_HISTORY");
		ResponseEntity<String> currency = band.patchJson("/api/products/" + shirt, """
				{"version": 0, "currency": "USD"}""", headers -> headers.setBearerAuth(admin));
		assertThat(currency.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(currency.getBody(), "$.code")).isEqualTo("PRODUCT_CURRENCY_LOCKED");
	}

	private int quantityOf(UUID variant) {
		return Integer.parseInt(OwnerSql.superuserQueryForString(
				"SELECT quantity_on_hand FROM bandcore.stock_levels WHERE variant_id = '%s'".formatted(variant)));
	}

	private <T> T inBand(Supplier<T> action) {
		return TenantContextHolder.callAs(new TenantContext(alpha, "alpha"), () -> transactions.execute(status -> action.get()));
	}

}

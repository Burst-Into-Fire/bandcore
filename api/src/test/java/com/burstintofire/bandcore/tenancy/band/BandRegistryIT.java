package com.burstintofire.bandcore.tenancy.band;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.PostgresTestDatabase;

class BandRegistryIT extends IntegrationTest {

	@Autowired
	BandRegistry registry;

	@Test
	void registersAnActiveBandWithDefaults() {
		Band band = registry.register("  The-Burst ", " The Burst ");

		Band stored = registry.findBySlug("the-burst").orElseThrow();
		assertThat(stored.getId()).isEqualTo(band.getId());
		assertThat(stored.getId().version()).isEqualTo(7);
		assertThat(stored.getName()).isEqualTo("The Burst");
		assertThat(stored.getCurrency()).isEqualTo("BRL");
		assertThat(stored.getTimezone()).isEqualTo("America/Sao_Paulo");
		assertThat(stored.getStatus()).isEqualTo(BandStatus.ACTIVE);
	}

	@Test
	void rejectsInvalidReservedAndTakenSlugsWithStableCodes() {
		registry.register("burst", "Burst");

		assertProblem(() -> registry.register("bu", "Burst"), ErrorCode.SLUG_INVALID);
		assertProblem(() -> registry.register("api", "Burst"), ErrorCode.SLUG_RESERVED);
		assertProblem(() -> registry.register("BURST", "Burst"), ErrorCode.SLUG_TAKEN);
	}

	@Test
	void databaseEnforcesCaseInsensitiveUniqueness() throws SQLException {
		try (Connection app = PostgresTestDatabase.appConnection(); Statement sql = app.createStatement()) {
			sql.execute(insert("burst"));

			assertThatThrownBy(() -> sql.execute(insert("Burst"))).isInstanceOf(SQLException.class)
				.hasMessageContaining(BandRegistry.SLUG_UNIQUE_INDEX);
		}
	}

	@Test
	void concurrentRegistrationsOfTheSameSlugCreateExactlyOneBand() throws Exception {
		int attempts = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Band>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
			for (int i = 0; i < attempts; i++) {
				Callable<Band> attempt = () -> {
					start.await();
					return registry.register("race", "Race");
				};
				results.add(pool.submit(attempt));
			}
			start.countDown();

			int created = 0;
			for (Future<Band> result : results) {
				try {
					result.get();
					created++;
				}
				catch (java.util.concurrent.ExecutionException ex) {
					assertThat(ex.getCause()).isInstanceOf(ApiException.class);
					assertThat(((ApiException) ex.getCause()).code()).isEqualTo(ErrorCode.SLUG_TAKEN);
				}
			}
			assertThat(created).isEqualTo(1);
		}
	}

	private static void assertProblem(Runnable action, ErrorCode code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(code));
	}

	private static String insert(String slug) {
		return """
				INSERT INTO bands (id, slug, name, created_at, updated_at)
				VALUES (gen_random_uuid(), '%s', 'Band', now(), now())
				""".formatted(slug);
	}

}

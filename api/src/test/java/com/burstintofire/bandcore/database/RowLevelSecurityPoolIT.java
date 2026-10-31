package com.burstintofire.bandcore.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.support.IntegrationTest;

/**
 * With a single pooled connection, every transaction reuses the same physical connection. The
 * band of one transaction must never be visible in the next one.
 */
@TestPropertySource(properties = { "spring.datasource.hikari.maximum-pool-size=1",
		"spring.datasource.hikari.minimum-idle=1" })
class RowLevelSecurityPoolIT extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void bandContextDoesNotLeakBetweenTransactionsOnTheSameConnection() {
		RowLevelSecurityIT.seed();
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		Integer firstBackend = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
		List<String> inA = as(RowLevelSecurityIT.BAND_A, tx);
		List<String> inB = as(RowLevelSecurityIT.BAND_B, tx);
		Integer lastBackend = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);

		assertThat(lastBackend).as("same physical connection").isEqualTo(firstBackend);
		assertThat(inA).containsExactly("Ana");
		assertThat(inB).containsExactly("Bia");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT coalesce(current_setting('app.band_id', true), '')", String.class))
			.isEmpty();
	}

	@Test
	void contextIsClearedAfterARollback() {
		RowLevelSecurityIT.seed();
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		TenantContextHolder.callAs(new TenantContext(RowLevelSecurityIT.BAND_A, "alpha"), () -> tx.execute(status -> {
			status.setRollbackOnly();
			return null;
		}));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isZero();
	}

	private List<String> as(UUID bandId, TransactionTemplate tx) {
		return TenantContextHolder.callAs(new TenantContext(bandId, "band"), () -> tx.execute(
				status -> jdbc.queryForList("SELECT display_name FROM members ORDER BY display_name", String.class)));
	}

}

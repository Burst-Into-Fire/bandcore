package com.burstintofire.bandcore.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.burstintofire.bandcore.shared.persistence.RowLevelSecurity;
import com.burstintofire.bandcore.shared.security.AuthenticatedUser;
import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

/**
 * Row-level security as the application sees it: connected as bandcore_app, with the context
 * set by the transaction manager from TenantContextHolder and the authenticated user.
 */
class RowLevelSecurityIT extends IntegrationTest {

	static final UUID BAND_A = UUID.fromString("0190a000-0000-7000-8000-00000000000a");

	static final UUID BAND_B = UUID.fromString("0190b000-0000-7000-8000-00000000000b");

	static final UUID USER_1 = UUID.fromString("0190c000-0000-7000-8000-000000000001");

	static final UUID USER_2 = UUID.fromString("0190c000-0000-7000-8000-000000000002");

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	RowLevelSecurity rowLevelSecurity;

	TransactionTemplate tx;

	@BeforeEach
	void twoBandsWithOneMemberEach() {
		tx = new TransactionTemplate(transactionManager);
		seed();
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	static void seed() {
		OwnerSql.global(TestRows.band(BAND_A, "alpha"), TestRows.band(BAND_B, "beta"));
		OwnerSql.inBand(BAND_A, TestRows.membership(BAND_A, USER_1), TestRows.membership(BAND_A, USER_2),
				TestRows.member(BAND_A, "Ana", USER_1));
		OwnerSql.inBand(BAND_B, TestRows.membership(BAND_B, USER_1), TestRows.member(BAND_B, "Bia", null));
	}

	@Test
	void withoutContextTenantTablesAreEmpty() {
		assertThat(inTransaction(() -> count("members"))).isZero();
		assertThat(inTransaction(() -> count("memberships"))).isZero();
	}

	@Test
	void outsideATransactionTenantTablesAreEmpty() {
		assertThat(count("members")).isZero();
	}

	@Test
	void bandContextSeesOnlyItsOwnRows() {
		assertThat(as(BAND_A, () -> names())).containsExactly("Ana");
		assertThat(as(BAND_B, () -> names())).containsExactly("Bia");
		assertThat(as(BAND_A, () -> count("memberships"))).isEqualTo(2);
	}

	@Test
	void userSeesOwnMembershipsInEveryBandButNoOneElses() {
		authenticate(USER_1);

		List<UUID> bands = inTransaction(
				() -> jdbc.queryForList("SELECT band_id FROM memberships ORDER BY band_id", UUID.class));

		assertThat(bands).containsExactly(BAND_A, BAND_B);
		assertThat(inTransaction(() -> count("members"))).as("members need the band context").isZero();
	}

	@Test
	void insertWithAnotherBandsIdIsRejected() {
		assertThatThrownBy(() -> as(BAND_A, () -> jdbc.update(TestRows.member(BAND_B, "Intruder", null))))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("violates row-level security policy");
		assertThat(as(BAND_B, () -> names())).containsExactly("Bia");
	}

	@Test
	void insertWithoutContextIsRejected() {
		assertThatThrownBy(() -> inTransaction(() -> jdbc.update(TestRows.member(BAND_A, "Nobody", null))))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("violates row-level security policy");
	}

	@Test
	void updateMovingARowToAnotherBandIsRejected() {
		assertThatThrownBy(() -> as(BAND_A, () -> jdbc.update("UPDATE members SET band_id = ?", BAND_B)))
			.isInstanceOf(DataAccessException.class);
		assertThat(as(BAND_A, () -> names())).containsExactly("Ana");
	}

	@Test
	void updatesAndDeletesNeverReachAnotherBand() {
		as(BAND_A, () -> jdbc.update("UPDATE members SET display_name = 'Changed'"));
		as(BAND_A, () -> jdbc.update("DELETE FROM members WHERE user_id IS NULL"));

		assertThat(as(BAND_B, () -> names())).containsExactly("Bia");
	}

	@Test
	void childRowCannotPointToAnotherBandsParent() {
		// USER_2 has a membership in band A only; a member of band B cannot be linked to it.
		assertThatThrownBy(() -> as(BAND_B, () -> jdbc.update(TestRows.member(BAND_B, "Linked", USER_2))))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("members_membership_fk");
	}

	@Test
	void bandCanBeSwitchedInsideATransactionForBandCreation() {
		List<String> names = inTransaction(() -> {
			rowLevelSecurity.switchBand(BAND_B, null);
			return names();
		});

		assertThat(names).containsExactly("Bia");
		assertThatThrownBy(() -> rowLevelSecurity.switchBand(BAND_B, null)).isInstanceOf(IllegalStateException.class);
	}

	private <T> T as(UUID bandId, java.util.function.Supplier<T> action) {
		return TenantContextHolder.callAs(new TenantContext(bandId, "band"), () -> inTransaction(action));
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return tx.execute(status -> action.get());
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private List<String> names() {
		return jdbc.queryForList("SELECT display_name FROM members ORDER BY display_name", String.class);
	}

	private static void authenticate(UUID userId) {
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(new AuthenticatedUser(userId, null, null, true), null));
	}

}

package com.burstintofire.bandcore.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.PostgresTestDatabase;

/**
 * Fails the build when a migration adds a tenant table without row-level security, policies,
 * a band_id index or composite foreign keys (docs/DATABASE.md, tenant table checklist).
 */
class SchemaGuardIT extends IntegrationTest {

	/**
	 * Tables without band_id. Adding one here needs a reason that survives review: global tables
	 * are reachable from every band, so they must never hold band data.
	 */
	static final Map<String, String> GLOBAL_TABLES = Map.of(
			"flyway_schema_history", "Flyway's migration history; the app role cannot read it",
			"bands", "the tenant itself; only the tenancy module reads or writes it",
			"users", "global accounts (ADR-0003): one account across bands; only the identity module uses it",
			"email_verification_tokens", "belongs to an account, used before any band is involved");

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void everyTenantTableIsProtected() throws SQLException {
		try (Connection connection = PostgresTestDatabase.ownerConnection()) {
			List<String> violations = new SchemaGuard(GLOBAL_TABLES).violations(connection);

			assertThat(violations).as("tenant table checklist, docs/DATABASE.md").isEmpty();
		}
	}

	@Test
	void guardDetectsUnprotectedTables() throws SQLException {
		try (Connection connection = PostgresTestDatabase.ownerConnection()) {
			connection.setAutoCommit(false);
			try (Statement ddl = connection.createStatement()) {
				// No RLS at all, no band_id index.
				ddl.execute("""
						CREATE TABLE bandcore.guard_no_rls (id uuid PRIMARY KEY,
						    band_id uuid NOT NULL REFERENCES bandcore.bands (id))
						""");
				// RLS forced but no policy; foreign key to a tenant table without band_id.
				ddl.execute("""
						CREATE TABLE bandcore.guard_no_policy (id uuid PRIMARY KEY,
						    band_id uuid NOT NULL REFERENCES bandcore.bands (id),
						    member_id uuid REFERENCES bandcore.members (id),
						    UNIQUE (band_id, id))
						""");
				ddl.execute("ALTER TABLE bandcore.guard_no_policy ENABLE ROW LEVEL SECURITY");
				ddl.execute("ALTER TABLE bandcore.guard_no_policy FORCE ROW LEVEL SECURITY");
				// A policy for reads only.
				ddl.execute("""
						CREATE TABLE bandcore.guard_read_policy (id uuid PRIMARY KEY, band_id uuid NOT NULL,
						    UNIQUE (band_id, id))
						""");
				ddl.execute("ALTER TABLE bandcore.guard_read_policy ENABLE ROW LEVEL SECURITY");
				ddl.execute("ALTER TABLE bandcore.guard_read_policy FORCE ROW LEVEL SECURITY");
				ddl.execute("""
						CREATE POLICY read_only ON bandcore.guard_read_policy FOR SELECT
						    USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)
						""");
				// Neither band_id nor in the allowlist.
				ddl.execute("CREATE TABLE bandcore.guard_global (id uuid PRIMARY KEY)");

				List<String> violations = new SchemaGuard(GLOBAL_TABLES).violations(connection);

				assertThat(violations).contains(
						"guard_no_rls: row-level security must be enabled and forced",
						"guard_no_rls: no policy on app.band_id for DELETE, INSERT, SELECT, UPDATE",
						"guard_no_rls: no index starting with band_id (add UNIQUE (band_id, id))",
						"guard_no_policy: no policy on app.band_id for DELETE, INSERT, SELECT, UPDATE",
						"guard_no_policy: foreign key guard_no_policy_member_id_fkey to tenant table members must include band_id on both sides",
						"guard_read_policy: no policy on app.band_id for DELETE, INSERT, UPDATE",
						"guard_global: no band_id column; add it, or add the table to the global allowlist with a reason");
			}
			finally {
				connection.rollback();
			}
		}
	}

	@Test
	void appRoleCannotBypassRowLevelSecurity() {
		var role = jdbc.queryForMap("""
				SELECT r.rolsuper, r.rolbypassrls,
				       (SELECT count(*) FROM pg_class c WHERE c.relowner = r.oid) AS owned
				FROM pg_roles r WHERE r.rolname = current_user
				""");

		assertThat(role).containsEntry("rolsuper", false).containsEntry("rolbypassrls", false).containsEntry("owned", 0L);
	}

}

package com.burstintofire.bandcore.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.PostgresTestDatabase;

/**
 * Row-level security only protects data if the runtime role neither owns the tables nor can
 * bypass RLS. These checks keep that true.
 */
class DatabaseRolesIT extends IntegrationTest {

	@Autowired
	DataSource dataSource;

	@Test
	void applicationConnectsAsTheAppRole() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(PostgresTestDatabase.APP_ROLE);
		assertThat(jdbc.queryForObject("SHOW TimeZone", String.class)).isEqualTo("UTC");
	}

	@Test
	void flywayRanAsTheOwner() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		// The app role cannot read Flyway's history, so check through the owner.
		assertThat(ownerQuery("SELECT DISTINCT installed_by FROM bandcore.flyway_schema_history"))
			.isEqualTo(PostgresTestDatabase.OWNER_ROLE);
		assertThat(ownerQuery("SELECT count(*) FROM bandcore.flyway_schema_history WHERE success")).isNotEqualTo("0");
		assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo("bandcore");
	}

	@Test
	void appRoleIsNotSuperuserAndCannotBypassRls() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		var role = jdbc.queryForMap("""
				SELECT rolsuper, rolbypassrls, rolcreaterole, rolcreatedb
				FROM pg_roles WHERE rolname = current_user
				""");

		assertThat(role).containsEntry("rolsuper", false)
			.containsEntry("rolbypassrls", false)
			.containsEntry("rolcreaterole", false)
			.containsEntry("rolcreatedb", false);
	}

	@Test
	void appRoleOwnsNoObjects() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		Integer owned = jdbc.queryForObject("""
				SELECT count(*) FROM pg_class c JOIN pg_roles r ON r.oid = c.relowner
				WHERE r.rolname = current_user
				""", Integer.class);
		Integer schemas = jdbc.queryForObject("""
				SELECT count(*) FROM pg_namespace n JOIN pg_roles r ON r.oid = n.nspowner
				WHERE r.rolname = current_user
				""", Integer.class);

		assertThat(owned).isZero();
		assertThat(schemas).isZero();
	}

	@Test
	void appRoleCannotRunDdl() throws SQLException {
		try (Connection connection = PostgresTestDatabase.appConnection();
				Statement statement = connection.createStatement()) {
			assertThatThrownBy(() -> statement.execute("CREATE TABLE bandcore.should_fail (id int)"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("permission denied");
			assertThatThrownBy(() -> statement.execute("CREATE TABLE public.should_fail (id int)"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("permission denied");
			assertThatThrownBy(() -> statement.execute("CREATE SCHEMA should_fail"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("permission denied");
		}
	}

	@Test
	void tablesCreatedByTheOwnerAreUsableButNotOwnedByTheAppRole() throws SQLException {
		try (Connection owner = PostgresTestDatabase.ownerConnection(); Statement ddl = owner.createStatement()) {
			ddl.execute("CREATE TABLE bandcore.grant_probe (id int)");
			try (Connection app = PostgresTestDatabase.appConnection(); Statement dml = app.createStatement()) {
				dml.execute("INSERT INTO bandcore.grant_probe VALUES (1)");
				dml.execute("UPDATE bandcore.grant_probe SET id = 2");
				dml.execute("DELETE FROM bandcore.grant_probe");
				assertThatThrownBy(() -> dml.execute("DROP TABLE bandcore.grant_probe")).isInstanceOf(SQLException.class)
					.hasMessageContaining("must be owner");
				assertThatThrownBy(() -> dml.execute("ALTER TABLE bandcore.grant_probe DISABLE ROW LEVEL SECURITY"))
					.isInstanceOf(SQLException.class)
					.hasMessageContaining("must be owner");
			}
			finally {
				ddl.execute("DROP TABLE bandcore.grant_probe");
			}
		}
	}

	private static String ownerQuery(String sql) {
		try (Connection connection = PostgresTestDatabase.ownerConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

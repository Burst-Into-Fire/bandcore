package com.burstintofire.bandcore.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inspects the schema in {@code pg_catalog} and lists every tenant table that breaks the
 * checklist in docs/DATABASE.md. A table is a tenant table unless it is in the global
 * allowlist; tenant tables must have:
 *
 * <ul>
 * <li>a {@code band_id NOT NULL} column;</li>
 * <li>row-level security enabled and forced;</li>
 * <li>policies on {@code app.band_id} covering SELECT, INSERT, UPDATE and DELETE;</li>
 * <li>{@code band_id} in every foreign key to another tenant table;</li>
 * <li>an index (or constraint) whose first column is {@code band_id}.</li>
 * </ul>
 */
final class SchemaGuard {

	static final String SCHEMA = "bandcore";

	private static final Set<String> ALL_COMMANDS = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

	private final Map<String, String> globalTables;

	/** @param globalTables tables without band_id, each with the reason it is global */
	SchemaGuard(Map<String, String> globalTables) {
		this.globalTables = globalTables;
	}

	List<String> violations(Connection connection) throws SQLException {
		List<String> violations = new ArrayList<>();
		List<String> tenantTables = new ArrayList<>();
		for (String table : tables(connection)) {
			if (!globalTables.containsKey(table)) {
				tenantTables.add(table);
			}
		}
		for (String table : tenantTables) {
			checkBandColumn(connection, table, violations);
			checkRowLevelSecurity(connection, table, violations);
			checkPolicies(connection, table, violations);
			checkBandIndex(connection, table, violations);
			checkForeignKeys(connection, table, new HashSet<>(tenantTables), violations);
		}
		return violations;
	}

	private static List<String> tables(Connection connection) throws SQLException {
		return strings(connection, """
				SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = ? AND c.relkind IN ('r', 'p') ORDER BY c.relname
				""", SCHEMA);
	}

	private static void checkBandColumn(Connection connection, String table, List<String> violations)
			throws SQLException {
		List<String> column = strings(connection, """
				SELECT format_type(a.atttypid, a.atttypmod) || CASE WHEN a.attnotnull THEN ' not null' ELSE '' END
				FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = ? AND c.relname = ? AND a.attname = 'band_id' AND NOT a.attisdropped
				""", SCHEMA, table);
		if (column.isEmpty()) {
			violations.add(table + ": no band_id column; add it, or add the table to the global allowlist with a reason");
		}
		else if (!column.getFirst().equals("uuid not null")) {
			violations.add(table + ": band_id must be uuid NOT NULL, found " + column.getFirst());
		}
	}

	private static void checkRowLevelSecurity(Connection connection, String table, List<String> violations)
			throws SQLException {
		List<String> flags = strings(connection, """
				SELECT c.relrowsecurity || ',' || c.relforcerowsecurity
				FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relname = ?
				""", SCHEMA, table);
		if (!flags.getFirst().equals("true,true")) {
			violations.add(table + ": row-level security must be enabled and forced");
		}
	}

	private static void checkPolicies(Connection connection, String table, List<String> violations)
			throws SQLException {
		List<String> commands = strings(connection, """
				SELECT cmd FROM pg_policies
				WHERE schemaname = ? AND tablename = ? AND permissive = 'PERMISSIVE'
				  AND (coalesce(qual, '') LIKE '%app.band_id%' OR coalesce(with_check, '') LIKE '%app.band_id%')
				""", SCHEMA, table);
		Set<String> covered = new HashSet<>();
		for (String command : commands) {
			if (command.equals("ALL")) {
				covered.addAll(ALL_COMMANDS);
			}
			else {
				covered.add(command);
			}
		}
		if (!covered.containsAll(ALL_COMMANDS)) {
			Set<String> missing = new HashSet<>(ALL_COMMANDS);
			missing.removeAll(covered);
			violations.add(table + ": no policy on app.band_id for " + String.join(", ", missing.stream().sorted().toList()));
		}
	}

	private static void checkBandIndex(Connection connection, String table, List<String> violations)
			throws SQLException {
		List<String> indexes = strings(connection, """
				SELECT i.indexrelid::regclass::text
				FROM pg_index i
				JOIN pg_class c ON c.oid = i.indrelid JOIN pg_namespace n ON n.oid = c.relnamespace
				JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0]
				WHERE n.nspname = ? AND c.relname = ? AND a.attname = 'band_id'
				""", SCHEMA, table);
		if (indexes.isEmpty()) {
			violations.add(table + ": no index starting with band_id (add UNIQUE (band_id, id))");
		}
	}

	private static void checkForeignKeys(Connection connection, String table, Set<String> tenantTables,
			List<String> violations) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT con.conname, target.relname,
				       EXISTS (
				           SELECT 1 FROM unnest(con.conkey, con.confkey) AS k(local_column, target_column)
				           JOIN pg_attribute la ON la.attrelid = con.conrelid AND la.attnum = k.local_column
				           JOIN pg_attribute ta ON ta.attrelid = con.confrelid AND ta.attnum = k.target_column
				           WHERE la.attname = 'band_id' AND ta.attname = 'band_id'
				       ) AS includes_band
				FROM pg_constraint con
				JOIN pg_class source ON source.oid = con.conrelid
				JOIN pg_class target ON target.oid = con.confrelid
				JOIN pg_namespace n ON n.oid = source.relnamespace
				WHERE con.contype = 'f' AND n.nspname = ? AND source.relname = ?
				""")) {
			statement.setString(1, SCHEMA);
			statement.setString(2, table);
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					if (tenantTables.contains(rs.getString(2)) && !rs.getBoolean(3)) {
						violations.add(table + ": foreign key " + rs.getString(1) + " to tenant table " + rs.getString(2)
								+ " must include band_id on both sides");
					}
				}
			}
		}
	}

	private static List<String> strings(Connection connection, String sql, String... parameters) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; i++) {
				statement.setString(i + 1, parameters[i]);
			}
			List<String> values = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					values.add(rs.getString(1));
				}
			}
			return values;
		}
	}

}

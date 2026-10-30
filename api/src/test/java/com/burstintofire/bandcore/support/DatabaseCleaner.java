package com.burstintofire.bandcore.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Empties every application table after each integration test, as the owner (the app role
 * cannot truncate). Flyway's history table is kept.
 */
public final class DatabaseCleaner {

	private DatabaseCleaner() {
	}

	public static void truncateAll() {
		try (Connection connection = PostgresTestDatabase.ownerConnection();
				Statement statement = connection.createStatement()) {
			List<String> tables = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("""
					SELECT quote_ident(tablename) FROM pg_tables
					WHERE schemaname = 'bandcore' AND tablename <> 'flyway_schema_history'
					""")) {
				while (rs.next()) {
					tables.add("bandcore." + rs.getString(1));
				}
			}
			if (!tables.isEmpty()) {
				statement.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Could not clean the test database", ex);
		}
	}

}

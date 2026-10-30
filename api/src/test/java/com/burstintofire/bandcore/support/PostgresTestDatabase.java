package com.burstintofire.bandcore.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL container for the whole test run, bootstrapped with the same role script as
 * docker-compose and production. Flyway connects as {@value #OWNER_ROLE}; the application
 * connects as {@value #APP_ROLE}, so row-level security and grants behave as in production.
 *
 * <p>Enable {@code testcontainers.reuse.enable=true} in {@code ~/.testcontainers.properties} to
 * keep the container between local runs.
 */
public final class PostgresTestDatabase {

	public static final String OWNER_ROLE = "bandcore_owner";

	public static final String APP_ROLE = "bandcore_app";

	private static final String DATABASE = "bandcore";

	private static final String OWNER_PASSWORD = "test_owner_password";

	private static final String APP_PASSWORD = "test_app_password";

	private static final String BOOTSTRAP = "db/bootstrap/";

	private static final String INIT_DIR = "/docker-entrypoint-initdb.d/";

	private static final PostgreSQLContainer CONTAINER = start();

	private PostgresTestDatabase() {
	}

	private static PostgreSQLContainer start() {
		// Set by Maven from the postgres.image property, so tests use the production image.
		String image = System.getProperty("bandcore.test.postgres-image", "postgres:17-alpine");
		PostgreSQLContainer container = new PostgreSQLContainer(
				DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
			.withDatabaseName(DATABASE)
			.withUsername("postgres")
			.withPassword("postgres")
			.withEnv("DB_OWNER_PASSWORD", OWNER_PASSWORD)
			.withEnv("DB_APP_PASSWORD", APP_PASSWORD)
			.withCopyFileToContainer(MountableFile.forHostPath(BOOTSTRAP + "00-create-roles.sh", 0755),
					INIT_DIR + "00-create-roles.sh")
			.withCopyFileToContainer(MountableFile.forHostPath(BOOTSTRAP + "create-roles.psql"),
					INIT_DIR + "create-roles.psql")
			.withReuse(true);
		container.start();
		return container;
	}

	/** Points the application at the container: runtime as the app role, Flyway as the owner. */
	public static void register(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
		registry.add("spring.datasource.username", () -> APP_ROLE);
		registry.add("spring.datasource.password", () -> APP_PASSWORD);
		registry.add("spring.flyway.user", () -> OWNER_ROLE);
		registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
	}

	/** Direct connection as the schema owner, for test setup and cleanup only. */
	public static Connection ownerConnection() throws SQLException {
		return DriverManager.getConnection(CONTAINER.getJdbcUrl(), OWNER_ROLE, OWNER_PASSWORD);
	}

	/** Direct connection as the runtime role, to prove what the application can and cannot do. */
	public static Connection appConnection() throws SQLException {
		return DriverManager.getConnection(CONTAINER.getJdbcUrl(), APP_ROLE, APP_PASSWORD);
	}

}

package com.burstintofire.bandcore.support;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.burstintofire.bandcore.shared.cache.ResettableCache;

/**
 * Base class for integration tests ({@code *IT}): the full application on a random port,
 * connected to the shared PostgreSQL container as the runtime role, with a fixed clock.
 * Tables and security caches are emptied after each test.
 *
 * <p>Test data builders ({@code aBand()}, {@code aUser()}, ...) live next to the entities they
 * build, in each module's test package, and are added together with those entities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.server.port=0")
@Import(TestClockConfiguration.class)
public abstract class IntegrationTest {

	@LocalServerPort
	protected int port;

	@LocalManagementPort
	protected int managementPort;

	protected ApiClient api;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		PostgresTestDatabase.register(registry);
	}

	@BeforeEach
	void createApiClient() {
		api = ApiClient.onPort(port);
	}

	@Autowired
	private List<ResettableCache> caches;

	@AfterEach
	void cleanDatabase() {
		DatabaseCleaner.truncateAll();
		caches.forEach(ResettableCache::clear);
	}

}

package com.burstintofire.bandcore.support;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.burstintofire.bandcore.shared.cache.ResettableCache;

/**
 * Base class for integration tests ({@code *IT}): the full application on a random port,
 * connected to the shared PostgreSQL container as the runtime role, with a test clock and an
 * in-process SMTP server ({@link TestMailServer}).
 * Tables and security caches are emptied after each test.
 *
 * <p>Test data builders ({@code aBand()}, {@code aUser()}, ...) live next to the entities they
 * build, in each module's test package, and are added together with those entities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.server.port=0")
@Import({ TestClockConfiguration.class, TestFixtures.class })
public abstract class IntegrationTest {

	@LocalServerPort
	protected int port;

	@LocalManagementPort
	protected int managementPort;

	protected ApiClient api;

	@Autowired
	protected TestFixtures fixtures;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		PostgresTestDatabase.register(registry);
		TestMailServer.register(registry);
	}

	@BeforeEach
	void createApiClient() {
		api = ApiClient.onPort(port);
	}

	@Autowired
	private List<ResettableCache> caches;

	/** Runs the @Async work (emails sent after commit). */
	@Autowired
	private ThreadPoolTaskExecutor applicationTaskExecutor;

	@AfterEach
	void cleanDatabase() {
		// Let emails of this test finish sending, so none arrives during the next test.
		awaitIdle(applicationTaskExecutor);
		DatabaseCleaner.truncateAll();
		caches.forEach(ResettableCache::clear);
		TestMailServer.purge();
		TestClockConfiguration.CLOCK.reset();
	}

	private static void awaitIdle(ThreadPoolTaskExecutor executor) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while ((executor.getActiveCount() > 0 || !executor.getThreadPoolExecutor().getQueue().isEmpty())
				&& System.nanoTime() < deadline) {
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

}

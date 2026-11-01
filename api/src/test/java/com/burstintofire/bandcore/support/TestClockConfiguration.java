package com.burstintofire.bandcore.support;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the system clock in integration tests, so every test sees the same "now" unless it
 * moves the clock with {@link #CLOCK}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	public static final Instant NOW = Instant.parse("2026-01-15T15:00:00Z");

	/** Shared by every application context of the test run; reset after each test. */
	public static final MutableTestClock CLOCK = new MutableTestClock();

	@Bean
	@Primary
	Clock testClock() {
		return CLOCK;
	}

}

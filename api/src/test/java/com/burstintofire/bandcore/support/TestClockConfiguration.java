package com.burstintofire.bandcore.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the system clock in integration tests, so every test sees the same "now". */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	public static final Instant NOW = Instant.parse("2026-01-15T15:00:00Z");

	@Bean
	@Primary
	Clock fixedTestClock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}

}

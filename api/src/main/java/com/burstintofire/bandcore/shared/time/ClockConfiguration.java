package com.burstintofire.bandcore.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only source of the current time. Inject {@link Clock} instead of calling
 * {@code Instant.now()} or {@code LocalDate.now()}, so tests can fix the time.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}

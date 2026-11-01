package com.burstintofire.bandcore.shared.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param limits by name: {@code capacity} attempts per {@code period}; the bucket refills
 *        completely at the end of each period
 */
@ConfigurationProperties("bandcore.rate-limit")
public record RateLimitProperties(Map<String, Window> limits) {

	public RateLimitProperties {
		limits = limits == null ? Map.of() : Map.copyOf(limits);
	}

	public record Window(long capacity, Duration period) {

		public Window {
			if (capacity < 1 || period == null || period.isNegative() || period.isZero()) {
				throw new IllegalArgumentException("A rate limit needs a capacity of at least 1 and a positive period");
			}
		}

	}

}

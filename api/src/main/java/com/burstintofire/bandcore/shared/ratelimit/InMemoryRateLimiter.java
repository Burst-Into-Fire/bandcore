package com.burstintofire.bandcore.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.cache.ResettableCache;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Buckets in this instance's memory. Enough while the API runs on one instance; with more, each
 * instance would allow the full limit, and this class is replaced by one backed by a shared
 * store (Bucket4j has PostgreSQL and Redis integrations). Time comes from the application
 * clock, so tests can move it.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
class InMemoryRateLimiter implements RateLimiter, ResettableCache {

	/** How often full (idle) buckets are dropped, so memory follows recent traffic only. */
	static final Duration SWEEP_INTERVAL = Duration.ofMinutes(5);

	private record Entry(Bucket bucket, long capacity) {
	}

	private final Map<String, RateLimitProperties.Window> windows;

	private final Clock clock;

	private final TimeMeter time;

	private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

	private final AtomicReference<Instant> nextSweep;

	InMemoryRateLimiter(RateLimitProperties properties, Clock clock) {
		this.windows = properties.limits();
		this.clock = clock;
		this.time = new ClockTimeMeter(clock);
		this.nextSweep = new AtomicReference<>(clock.instant().plus(SWEEP_INTERVAL));
	}

	@Override
	public RateLimit limit(String name) {
		RateLimitProperties.Window window = windows.get(name);
		if (window == null) {
			throw new IllegalStateException("Rate limit '" + name + "' is not configured in bandcore.rate-limit.limits");
		}
		return new Limit(name, window);
	}

	@Override
	public void clear() {
		buckets.clear();
	}

	private Entry bucket(String id, RateLimitProperties.Window window) {
		sweepWhenDue();
		return buckets.computeIfAbsent(id, ignored -> new Entry(Bucket.builder()
			.addLimit(limit -> limit.capacity(window.capacity()).refillIntervally(window.capacity(), window.period()))
			.withCustomTimePrecision(time)
			.build(), window.capacity()));
	}

	private void sweepWhenDue() {
		Instant now = clock.instant();
		Instant due = nextSweep.get();
		if (now.isBefore(due) || !nextSweep.compareAndSet(due, now.plus(SWEEP_INTERVAL))) {
			return;
		}
		buckets.values().removeIf(entry -> entry.bucket().getAvailableTokens() >= entry.capacity());
	}

	private final class Limit implements RateLimit {

		private final String name;

		private final RateLimitProperties.Window window;

		Limit(String name, RateLimitProperties.Window window) {
			this.name = name;
			this.window = window;
		}

		@Override
		public void consume(String key) {
			ConsumptionProbe probe = bucket(id(key), window).bucket().tryConsumeAndReturnRemaining(1);
			if (!probe.isConsumed()) {
				throw tooManyRequests(probe.getNanosToWaitForRefill());
			}
		}

		@Override
		public void refund(String key) {
			Entry entry = buckets.get(id(key));
			if (entry != null) {
				entry.bucket().addTokens(1);
			}
		}

		private String id(String key) {
			return name + '\u0000' + (key == null ? "" : key);
		}

	}

	private static ApiException tooManyRequests(long nanosToWait) {
		long seconds = Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L);
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS,
				"Too many attempts. Try again later.")
			.withHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
	}

	/** Bucket4j time from the application clock (wall clock based, nanosecond resolution). */
	private record ClockTimeMeter(Clock clock) implements TimeMeter {

		@Override
		public long currentTimeNanos() {
			Instant now = clock.instant();
			return now.getEpochSecond() * 1_000_000_000L + now.getNano();
		}

		@Override
		public boolean isWallClockBased() {
			return true;
		}

	}

}

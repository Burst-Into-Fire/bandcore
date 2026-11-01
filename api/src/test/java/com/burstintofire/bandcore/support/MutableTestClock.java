package com.burstintofire.bandcore.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The application clock in integration tests. It starts at {@link TestClockConfiguration#NOW}
 * and only moves when a test calls {@link #advance}; {@link IntegrationTest} resets it after
 * each test.
 */
public final class MutableTestClock extends Clock {

	private final AtomicReference<Instant> now = new AtomicReference<>(TestClockConfiguration.NOW);

	public void advance(Duration duration) {
		now.updateAndGet(current -> current.plus(duration));
	}

	public void reset() {
		now.set(TestClockConfiguration.NOW);
	}

	@Override
	public Instant instant() {
		return now.get();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("The test clock is always UTC");
	}

}

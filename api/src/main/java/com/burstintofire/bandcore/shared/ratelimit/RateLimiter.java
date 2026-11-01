package com.burstintofire.bandcore.shared.ratelimit;

/**
 * Token-bucket rate limits, configured by name in {@code bandcore.rate-limit.limits} and
 * documented in docs/security/rate-limits.md. Resolve each limit once, in the constructor of the
 * class that uses it, so a missing configuration stops the startup.
 */
public interface RateLimiter {

	/** @throws IllegalStateException when the limit is not configured */
	RateLimit limit(String name);

	/** One limit, applied separately to each key (an IP address, an email, a user id). */
	interface RateLimit {

		/**
		 * Takes one attempt.
		 * @throws com.burstintofire.bandcore.shared.web.ApiException 429 with {@code Retry-After}
		 * when no attempt is left
		 */
		void consume(String key);

		/** Gives back an attempt taken by {@link #consume}, for example after a successful login. */
		void refund(String key);

	}

}

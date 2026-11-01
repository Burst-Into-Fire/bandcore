package com.burstintofire.bandcore.shared.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Small in-memory cache with a fixed time to live and explicit eviction, for security lookups
 * that must stay fresh (bands by slug, memberships). Entries are evicted as soon as the data
 * changes; the time to live only bounds how long a missed eviction can last.
 *
 * <p>Empty results are not cached, so new data is visible immediately.
 */
public final class ExpiringCache<K, V> {

	private record Entry<V>(V value, Instant expiresAt) {
	}

	private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

	private final Duration timeToLive;

	private final int maxSize;

	private final Clock clock;

	public ExpiringCache(Duration timeToLive, int maxSize, Clock clock) {
		this.timeToLive = timeToLive;
		this.maxSize = maxSize;
		this.clock = clock;
	}

	public Optional<V> get(K key, Function<K, Optional<V>> loader) {
		Instant now = clock.instant();
		Entry<V> entry = entries.get(key);
		if (entry != null && now.isBefore(entry.expiresAt())) {
			return Optional.of(entry.value());
		}
		Optional<V> loaded = loader.apply(key);
		if (loaded.isPresent() && !timeToLive.isZero()) {
			if (entries.size() >= maxSize) {
				entries.clear();
			}
			entries.put(key, new Entry<>(loaded.get(), now.plus(timeToLive)));
		}
		else {
			entries.remove(key);
		}
		return loaded;
	}

	public void evict(K key) {
		entries.remove(key);
	}

	public void evictIf(java.util.function.Predicate<K> condition) {
		entries.keySet().removeIf(condition);
	}

	public void clear() {
		entries.clear();
	}

}

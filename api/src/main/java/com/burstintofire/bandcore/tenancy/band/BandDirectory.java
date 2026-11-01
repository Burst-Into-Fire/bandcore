package com.burstintofire.bandcore.tenancy.band;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.cache.ExpiringCache;
import com.burstintofire.bandcore.shared.cache.ResettableCache;

/**
 * Resolves slugs to bands on every request through a short cache. Changes to a band's status
 * evict its entry, so a suspension takes effect on the next request.
 */
@Component
public class BandDirectory implements ResettableCache {

	public record BandSnapshot(UUID id, String slug, String name, BandStatus status) {

		public boolean isActive() {
			return status == BandStatus.ACTIVE;
		}

	}

	private final BandRepository bands;

	private final ExpiringCache<String, BandSnapshot> cache;

	public BandDirectory(BandRepository bands, Clock clock,
			@Value("${bandcore.tenancy.band-cache-ttl:60s}") Duration timeToLive) {
		this.bands = bands;
		this.cache = new ExpiringCache<>(timeToLive, 10_000, clock);
	}

	public Optional<BandSnapshot> findBySlug(String slug) {
		return cache.get(slug, key -> bands.findBySlug(key).map(BandDirectory::snapshot));
	}

	public void evict(String slug) {
		cache.evict(slug);
	}

	@Override
	public void clear() {
		cache.clear();
	}

	static BandSnapshot snapshot(Band band) {
		return new BandSnapshot(band.getId(), band.getSlug(), band.getName(), band.getStatus());
	}

}

package com.burstintofire.bandcore.tenancy.membership;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.BandAccess;
import com.burstintofire.bandcore.shared.cache.ExpiringCache;
import com.burstintofire.bandcore.shared.cache.ResettableCache;

/**
 * Answers the identity module's band access questions from the memberships table, on every
 * authenticated request. Results are cached briefly (60 seconds by default) and evicted as soon
 * as a membership changes, so a removed member or a downgraded role takes effect on the next
 * request, not when the access token expires.
 */
@Component
public class MembershipAccess implements BandAccess, ResettableCache {

	private record Key(UUID bandId, UUID userId) {
	}

	private final MembershipRepository memberships;

	private final ExpiringCache<Key, Grant> cache;

	MembershipAccess(MembershipRepository memberships, Clock clock,
			@Value("${bandcore.tenancy.membership-cache-ttl:60s}") Duration timeToLive) {
		if (timeToLive.compareTo(Duration.ofSeconds(60)) > 0) {
			throw new IllegalStateException("The membership cache must not keep entries for more than 60 seconds");
		}
		this.memberships = memberships;
		this.cache = new ExpiringCache<>(timeToLive, 50_000, clock);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Grant> find(UUID bandId, UUID userId) {
		return cache.get(new Key(bandId, userId),
				key -> memberships.findByBandIdAndUserId(key.bandId(), key.userId())
					.filter(Membership::isActive)
					.map(membership -> new Grant(membership.getRole().name(), Set.of())));
	}

	/** Call after any change to a membership. */
	public void evict(UUID bandId, UUID userId) {
		cache.evict(new Key(bandId, userId));
	}

	@Override
	public void clear() {
		cache.clear();
	}

}

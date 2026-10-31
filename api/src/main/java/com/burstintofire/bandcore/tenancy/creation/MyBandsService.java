package com.burstintofire.bandcore.tenancy.creation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.tenancy.band.Band;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;
import com.burstintofire.bandcore.tenancy.membership.Membership;
import com.burstintofire.bandcore.tenancy.membership.MembershipRepository;
import com.burstintofire.bandcore.tenancy.membership.MembershipStatus;

/**
 * Lists the current user's bands on the central host. Memberships are read through the
 * {@code app.user_id} RLS policy, which only exposes the user's own rows.
 */
@Service
class MyBandsService {

	record MyBand(UUID id, String slug, String name, String role, String url) {
	}

	private final MembershipRepository memberships;

	private final BandRegistry bands;

	private final PlatformProperties platform;

	MyBandsService(MembershipRepository memberships, BandRegistry bands, PlatformProperties platform) {
		this.memberships = memberships;
		this.bands = bands;
		this.platform = platform;
	}

	@Transactional(readOnly = true)
	List<MyBand> list() {
		UUID userId = CurrentUser.require().userId();
		List<Membership> active = memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
		Map<UUID, Band> byId = bands.findAll(active.stream().map(Membership::getBandId).toList())
			.stream()
			.collect(Collectors.toMap(Band::getId, Function.identity()));
		return active.stream()
			.map(membership -> byId.get(membership.getBandId()))
			.filter(band -> band != null && band.isActive())
			.sorted(Comparator.comparing(Band::getName, String.CASE_INSENSITIVE_ORDER))
			.map(band -> new MyBand(band.getId(), band.getSlug(), band.getName(), roleIn(active, band.getId()),
					platform.bandUrl(band.getSlug())))
			.toList();
	}

	private static String roleIn(List<Membership> memberships, UUID bandId) {
		return memberships.stream()
			.filter(membership -> membership.getBandId().equals(bandId))
			.findFirst()
			.map(membership -> membership.getRole().name())
			.orElseThrow();
	}

}

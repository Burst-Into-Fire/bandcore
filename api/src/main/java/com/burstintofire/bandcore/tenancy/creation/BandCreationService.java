package com.burstintofire.bandcore.tenancy.creation;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.Accounts;
import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.persistence.RowLevelSecurity;
import com.burstintofire.bandcore.shared.security.AuthenticatedUser;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.BandCreated;
import com.burstintofire.bandcore.tenancy.Role;
import com.burstintofire.bandcore.tenancy.band.Band;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;
import com.burstintofire.bandcore.tenancy.member.Member;
import com.burstintofire.bandcore.tenancy.member.MemberRepository;
import com.burstintofire.bandcore.tenancy.membership.Membership;
import com.burstintofire.bandcore.tenancy.membership.MembershipRepository;

/**
 * Self-service band creation on the central host: any verified account can create a band and
 * becomes its first admin and first member, in one transaction.
 */
@Service
public class BandCreationService {

	public record CreatedBand(Band band, String url) {
	}

	private final BandRegistry registry;

	private final MembershipRepository memberships;

	private final MemberRepository members;

	private final Accounts accounts;

	private final RowLevelSecurity rowLevelSecurity;

	private final ApplicationEventPublisher events;

	private final PlatformProperties platform;

	private final IdGenerator ids;

	private final Clock clock;

	private final int maxBandsPerUser;

	BandCreationService(BandRegistry registry, MembershipRepository memberships, MemberRepository members,
			Accounts accounts, RowLevelSecurity rowLevelSecurity, ApplicationEventPublisher events,
			PlatformProperties platform, IdGenerator ids, Clock clock,
			@Value("${bandcore.tenancy.max-bands-per-user:5}") int maxBandsPerUser) {
		this.registry = registry;
		this.memberships = memberships;
		this.members = members;
		this.accounts = accounts;
		this.rowLevelSecurity = rowLevelSecurity;
		this.events = events;
		this.platform = platform;
		this.ids = ids;
		this.clock = clock;
		this.maxBandsPerUser = maxBandsPerUser;
	}

	@Transactional
	public CreatedBand create(String name, String slug) {
		AuthenticatedUser user = CurrentUser.require();
		Accounts.Account account = accounts.find(user.userId())
			.filter(Accounts.Account::active)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied."));
		if (!account.emailVerified()) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED,
					"Confirm your email before creating a band.");
		}
		if (registry.countCreatedBy(user.userId()) >= maxBandsPerUser) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.BAND_LIMIT_REACHED,
					"You have reached the limit of " + maxBandsPerUser + " bands.");
		}

		Band band = registry.register(slug, name, user.userId());
		// The rest of the transaction writes the new band's rows: switch the RLS context to it.
		rowLevelSecurity.switchBand(band.getId(), user.userId());
		Instant now = clock.instant();
		memberships.save(new Membership(ids.newId(), band.getId(), user.userId(), Role.ADMIN, now));
		members.save(new Member(ids.newId(), band.getId(), account.displayName(), user.userId(), now));
		members.flush();
		events.publishEvent(new BandCreated(band.getId(), band.getSlug(), user.userId()));
		return new CreatedBand(band, platform.bandUrl(band.getSlug()));
	}

}

package com.burstintofire.bandcore.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.BandAccess;
import com.burstintofire.bandcore.identity.events.SecurityEventType;
import com.burstintofire.bandcore.identity.events.SecurityEvents;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.identity.user.UserRepository;
import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.SecretTokens;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Moves a session to another host without the password (M4-05). Sessions are host-only, so the
 * current host issues a code for one target host, and that host exchanges it for its own session.
 * The code is in the URL fragment, which browsers never send to servers.
 */
@Service
public class HandoffService {

	static final Duration LIFETIME = Duration.ofSeconds(60);

	private final HandoffCodeRepository codes;

	private final UserRepository users;

	private final BandAccess bandAccess;

	private final SessionService sessions;

	private final SecurityEvents securityEvents;

	private final PlatformProperties platform;

	private final IdGenerator ids;

	private final Clock clock;

	HandoffService(HandoffCodeRepository codes, UserRepository users, BandAccess bandAccess, SessionService sessions,
			SecurityEvents securityEvents, PlatformProperties platform, IdGenerator ids, Clock clock) {
		this.codes = codes;
		this.users = users;
		this.bandAccess = bandAccess;
		this.sessions = sessions;
		this.securityEvents = securityEvents;
		this.platform = platform;
		this.ids = ids;
		this.clock = clock;
	}

	/**
	 * A link to the band with this slug, or to the central host when {@code slug} is null. A band
	 * needs a verified email and active access; otherwise 403, the same for a slug that does not
	 * exist.
	 */
	@Transactional
	public String issue(String slug) {
		UUID userId = CurrentUser.require().userId();
		User user = users.findById(userId).filter(User::isActive).orElseThrow(HandoffService::noAccess);
		UUID bandId = null;
		String host = platform.centralHost();
		String url = platform.centralUrl();
		if (slug != null) {
			bandId = bandAccess.activeBandId(slug).orElseThrow(HandoffService::noAccess);
			if (!user.isEmailVerified() || bandAccess.find(bandId, userId).isEmpty()) {
				throw noAccess();
			}
			host = platform.bandHost(slug);
			url = platform.bandUrl(slug);
		}
		Instant now = clock.instant();
		codes.deleteExpiredBefore(now);
		String code = SecretTokens.generate();
		codes.save(new HandoffCode(ids.newId(), SecretTokens.hash(code), userId, bandId, host, now.plus(LIFETIME), now));
		return url + "/auth/handoff#code=" + code;
	}

	/**
	 * A session on this host for the code's user. Used, expired, unknown or other-host codes all
	 * answer 401; so does a code whose user lost access to the band since it was issued.
	 */
	@Transactional
	public SessionService.Session exchange(String code) {
		TenantContextHolder.Scope scope = TenantContextHolder.scope().orElseThrow(HandoffService::invalidCode);
		Instant now = clock.instant();
		HandoffCode handoff = codes.findByCodeHash(SecretTokens.hash(code == null ? "" : code))
			.filter(candidate -> candidate.isUsableOn(scope.host(), now))
			.orElseThrow(HandoffService::invalidCode);
		UUID scopeBand = scope.isCentral() ? null : scope.tenant().bandId();
		if (!Objects.equals(handoff.getTargetBand(), scopeBand)) {
			throw invalidCode();
		}
		handoff.markUsed(now);
		codes.flush();
		SessionService.Session session = sessions.openWithoutPassword(handoff.getUserId())
			.orElseThrow(HandoffService::invalidCode);
		securityEvents.record(SecurityEventType.LOGIN_SUCCEEDED, handoff.getUserId(), Map.of("method", "handoff"));
		return session;
	}

	private static ApiException noAccess() {
		return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "You have no access to this band.");
	}

	private static ApiException invalidCode() {
		return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
				"The link has expired or was already used. Choose the band again.");
	}

}

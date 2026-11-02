package com.burstintofire.bandcore.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.BandAccess;
import com.burstintofire.bandcore.identity.BandSessions;
import com.burstintofire.bandcore.identity.events.SecurityEventType;
import com.burstintofire.bandcore.identity.events.SecurityEvents;
import com.burstintofire.bandcore.identity.token.AccessTokenService;
import com.burstintofire.bandcore.identity.token.AccessTokenService.AccessToken;
import com.burstintofire.bandcore.identity.token.JwtProperties;
import com.burstintofire.bandcore.identity.user.Emails;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.identity.user.UserRepository;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.SecretTokens;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Sessions are scoped to one host. On a band host, login and refresh require a verified email
 * and an active membership in that band; on the central host they give a central session
 * (no band), limited to "my bands" and band creation.
 */
@Service
public class SessionService implements BandSessions {

	/** Tokens of a successful login or refresh; the refresh token goes only into the cookie. */
	public record Session(AccessToken accessToken, String refreshToken, Duration refreshTokenMaxAge) {

		@Override
		public String toString() {
			return "Session[]";
		}

	}

	/** Refresh refused: the client must log in again. The cookie is cleared. */
	public static class RefreshRejected extends RuntimeException {

		RefreshRejected() {
			super("Refresh rejected", null, false, false);
		}

	}

	private final UserRepository users;

	private final RefreshTokenRepository refreshTokens;

	private final BandAccess bandAccess;

	private final AccessTokenService accessTokens;

	private final PasswordEncoder passwordEncoder;

	private final JwtProperties properties;

	private final IdGenerator ids;

	private final SecurityEvents securityEvents;

	private final Clock clock;

	/** Compared against when the email is unknown, so the response time does not reveal it. */
	private final String dummyHash;

	SessionService(UserRepository users, RefreshTokenRepository refreshTokens, BandAccess bandAccess,
			AccessTokenService accessTokens, PasswordEncoder passwordEncoder, JwtProperties properties, IdGenerator ids,
			SecurityEvents securityEvents, Clock clock) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.bandAccess = bandAccess;
		this.accessTokens = accessTokens;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.ids = ids;
		this.securityEvents = securityEvents;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode(SecretTokens.generate());
	}

	/** Wrong password, unknown email, unverified email and missing membership all look the same. */
	@Transactional
	public Session login(String rawEmail, String password) {
		TenantContextHolder.Scope scope = requireScope();
		Optional<User> found = users.findByEmail(Emails.normalize(rawEmail));
		if (found.isEmpty()) {
			passwordEncoder.matches(password, dummyHash);
			throw denied(null, "unknown_email");
		}
		User user = found.get();
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw denied(user.getId(), "wrong_password");
		}
		if (!user.isActive()) {
			throw denied(user.getId(), "account_disabled");
		}
		if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
			user.changePasswordHash(passwordEncoder.encode(password), clock.instant());
		}
		String role = null;
		if (!scope.isCentral()) {
			UUID bandId = scope.tenant().bandId();
			if (!user.isEmailVerified()) {
				throw denied(user.getId(), "email_not_verified");
			}
			role = bandAccess.find(bandId, user.getId())
				.orElseThrow(() -> denied(user.getId(), "no_membership"))
				.role();
		}
		securityEvents.record(SecurityEventType.LOGIN_SUCCEEDED, user.getId());
		Instant now = clock.instant();
		return newToken(user.getId(), scope, role, UUID.randomUUID(), now, now.plus(properties.refreshTokenAbsoluteLifetime()));
	}

	/**
	 * A new session on the current host for a user already proven by another means (a handoff
	 * code), with the same account, email and membership checks as a login. Empty when any fails.
	 */
	@Transactional
	Optional<Session> openWithoutPassword(UUID userId) {
		TenantContextHolder.Scope scope = requireScope();
		User user = users.findById(userId).filter(User::isActive).orElse(null);
		if (user == null) {
			return Optional.empty();
		}
		String role = null;
		if (!scope.isCentral()) {
			if (!user.isEmailVerified()) {
				return Optional.empty();
			}
			Optional<BandAccess.Grant> grant = bandAccess.find(scope.tenant().bandId(), userId);
			if (grant.isEmpty()) {
				return Optional.empty();
			}
			role = grant.get().role();
		}
		Instant now = clock.instant();
		return Optional.of(newToken(userId, scope, role, UUID.randomUUID(), now,
				now.plus(properties.refreshTokenAbsoluteLifetime())));
	}

	/**
	 * Rotates the refresh token. Reusing a token that was already rotated means it was copied:
	 * the whole family is revoked, so both the thief's and the user's tokens stop working.
	 */
	@Transactional(noRollbackFor = RefreshRejected.class)
	public Session refresh(String rawToken) {
		TenantContextHolder.Scope scope = requireScope();
		Instant now = clock.instant();
		RefreshToken token = refreshTokens.findByTokenHash(SecretTokens.hash(rawToken))
			.orElseThrow(RefreshRejected::new);
		if (token.isRevoked()) {
			throw new RefreshRejected();
		}
		if (token.isRotated()) {
			refreshTokens.revokeFamily(token.getFamilyId(), now);
			securityEvents.record(SecurityEventType.REFRESH_TOKEN_REUSED, token.getUserId(),
					Map.of("family_id", token.getFamilyId().toString()));
			throw new RefreshRejected();
		}
		if (token.isExpired(now) || !token.getAudience().equals(scope.host())) {
			throw new RefreshRejected();
		}
		User user = users.findById(token.getUserId()).filter(User::isActive).orElse(null);
		String role = null;
		if (user != null && !scope.isCentral()) {
			role = user.isEmailVerified()
					? bandAccess.find(scope.tenant().bandId(), user.getId()).map(BandAccess.Grant::role).orElse(null)
					: null;
		}
		if (user == null || (!scope.isCentral() && role == null)) {
			refreshTokens.revokeFamily(token.getFamilyId(), now);
			throw new RefreshRejected();
		}
		token.markRotated(now);
		return newToken(user.getId(), scope, role, token.getFamilyId(), now, token.getFamilyExpiresAt());
	}

	/** Revokes the session of this cookie, if it still exists. */
	@Transactional
	public void logout(String rawToken) {
		refreshTokens.findByTokenHash(SecretTokens.hash(rawToken))
			.ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
	}

	@Override
	@Transactional
	public void endSessionsInBand(UUID userId, UUID bandId) {
		refreshTokens.revokeAllOfUserInBand(userId, bandId, clock.instant());
	}

	/** "Log out everywhere", asked by the user. */
	@Transactional
	public void logoutAll(UUID userId) {
		logoutEverywhere(userId);
		securityEvents.record(SecurityEventType.LOGOUT_ALL, userId);
	}

	/**
	 * Ends every session of the user, on every host: refresh tokens are revoked and access
	 * tokens issued until now are refused (see {@link User#endAllSessions}).
	 */
	@Transactional
	public void logoutEverywhere(UUID userId) {
		Instant now = clock.instant();
		refreshTokens.revokeAllOfUser(userId, now);
		users.findById(userId).ifPresent(user -> user.endAllSessions(now));
	}

	private Session newToken(UUID userId, TenantContextHolder.Scope scope, String role, UUID familyId, Instant now,
			Instant familyExpiresAt) {
		UUID bandId = scope.isCentral() ? null : scope.tenant().bandId();
		Instant expiresAt = min(now.plus(properties.refreshTokenIdleLifetime()), familyExpiresAt);
		String raw = SecretTokens.generate();
		refreshTokens.save(new RefreshToken(ids.newId(), familyId, userId, bandId, scope.host(),
				SecretTokens.hash(raw), now, expiresAt, familyExpiresAt));
		AccessToken access = accessTokens.issue(userId, scope.host(), bandId, role);
		return new Session(access, raw, Duration.between(now, expiresAt));
	}

	private static TenantContextHolder.Scope requireScope() {
		return TenantContextHolder.scope().orElseThrow(() -> new IllegalStateException("No host scope"));
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	/** A refused login: records why (LOGIN_FAILED), then answers the same generic error for every reason. */
	private ApiException denied(UUID userId, String reason) {
		securityEvents.record(SecurityEventType.LOGIN_FAILED, userId, Map.of("reason", reason));
		return invalidCredentials();
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
				"Email or password is incorrect, or this account has no access here.");
	}

}

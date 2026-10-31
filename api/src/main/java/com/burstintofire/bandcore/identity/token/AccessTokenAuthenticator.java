package com.burstintofire.bandcore.identity.token;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.identity.BandAccess;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.identity.user.UserRepository;
import com.burstintofire.bandcore.shared.security.AuthenticatedUser;
import com.burstintofire.bandcore.shared.tenant.TenantContext;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;

/**
 * Turns a token whose signature, expiry and issuer are valid into an authenticated request, or
 * rejects it with 401. A valid signature is not enough:
 *
 * <ul>
 * <li>{@code aud} must be the request host, so a token never works on another subdomain;</li>
 * <li>on a band host, {@code band_id} must be that band; on the central host it must be absent;</li>
 * <li>the account must still be active, and on a band host the email verified and the membership
 * active. The role comes from the database, never from the token.</li>
 * </ul>
 */
@Component
class AccessTokenAuthenticator implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserRepository users;

	private final BandAccess bandAccess;

	AccessTokenAuthenticator(UserRepository users, BandAccess bandAccess) {
		this.users = users;
		this.bandAccess = bandAccess;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		TenantContextHolder.Scope scope = TenantContextHolder.scope().orElseThrow(AccessTokenAuthenticator::rejected);
		if (jwt.getAudience() == null || !jwt.getAudience().contains(scope.host())) {
			throw rejected();
		}
		UUID userId = uuid(jwt.getSubject()).orElseThrow(AccessTokenAuthenticator::rejected);
		String bandClaim = jwt.getClaimAsString(TokenClaims.BAND_ID);
		User user = users.findById(userId).filter(User::isActive).orElseThrow(AccessTokenAuthenticator::rejected);

		if (scope.isCentral()) {
			if (bandClaim != null) {
				throw rejected();
			}
			return new BandcoreAuthentication(new AuthenticatedUser(userId, null, null, user.isEmailVerified()), jwt,
					List.of());
		}

		TenantContext tenant = scope.tenant();
		if (!Objects.equals(bandClaim, tenant.bandId().toString()) || !user.isEmailVerified()) {
			throw rejected();
		}
		BandAccess.Grant grant = bandAccess.find(tenant.bandId(), userId).orElseThrow(AccessTokenAuthenticator::rejected);
		var authorities = grant.permissions().stream().map(SimpleGrantedAuthority::new).toList();
		return new BandcoreAuthentication(new AuthenticatedUser(userId, tenant.bandId(), grant.role(), true), jwt,
				authorities);
	}

	private static Optional<UUID> uuid(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return Optional.empty();
		}
	}

	private static InvalidBearerTokenException rejected() {
		// Same message for every reason: the response never says why a token was refused.
		return new InvalidBearerTokenException("Invalid token");
	}

}

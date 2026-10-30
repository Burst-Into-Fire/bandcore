package com.burstintofire.bandcore.identity.token;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.burstintofire.bandcore.shared.security.AuthenticatedUser;

/** An authenticated request: the validated principal and the permissions of its role. */
public class BandcoreAuthentication extends AbstractAuthenticationToken {

	private final AuthenticatedUser principal;

	private final transient Jwt token;

	public BandcoreAuthentication(AuthenticatedUser principal, Jwt token,
			Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		this.token = token;
		setAuthenticated(true);
	}

	@Override
	public AuthenticatedUser getPrincipal() {
		return principal;
	}

	@Override
	public Object getCredentials() {
		return token;
	}

	@Override
	public String getName() {
		return principal.userId().toString();
	}

}

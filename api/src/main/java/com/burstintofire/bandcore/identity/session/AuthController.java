package com.burstintofire.bandcore.identity.session;

import java.time.Clock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.identity.events.SecurityEventType;
import com.burstintofire.bandcore.identity.events.SecurityEvents;
import com.burstintofire.bandcore.identity.user.Emails;
import com.burstintofire.bandcore.shared.ratelimit.RateLimiter;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.web.AnyHostEndpoint;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ClientAddresses;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.shared.web.PublicEndpoint;

/**
 * Login, refresh and logout on the central host and on band hosts. The access token is
 * returned in the body (the web app keeps it in memory); the refresh token only in an
 * {@code HttpOnly} cookie. Unsafe requests here need {@code X-Requested-With} (CSRF).
 */
@RestController
@RequestMapping("/auth")
@AnyHostEndpoint
@Tag(name = "Sessions")
class AuthController {

	private final SessionService sessions;

	private final Clock clock;

	private final RateLimiter.RateLimit loginsPerIp;

	private final RateLimiter.RateLimit loginFailuresPerAccount;

	private final ClientAddresses clients;

	private final SecurityEvents securityEvents;

	private final HandoffService handoffs;

	private final RateLimiter.RateLimit handoffExchangesPerIp;

	AuthController(SessionService sessions, HandoffService handoffs, Clock clock, RateLimiter limiter,
			ClientAddresses clients, SecurityEvents securityEvents) {
		this.sessions = sessions;
		this.handoffs = handoffs;
		this.handoffExchangesPerIp = limiter.limit("handoff-exchange-ip");
		this.securityEvents = securityEvents;
		this.clock = clock;
		this.loginsPerIp = limiter.limit("login-ip");
		this.loginFailuresPerAccount = limiter.limit("login-failures-account");
		this.clients = clients;
	}

	@PostMapping("/login")
	@PublicEndpoint("logging in is how a session starts")
	@Operation(operationId = "login", summary = "Log in on this host and get an access token",
			description = "Sets the refresh token cookie. Any failure answers the same 401 INVALID_CREDENTIALS.")
	ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		String account = Emails.normalize(request.email());
		try {
			loginsPerIp.consume(clients.clientOf(http));
			// Every attempt takes one from the account's bucket and a success gives it back, so
			// only failures count, and parallel guesses cannot all slip past the check.
			loginFailuresPerAccount.consume(account);
		}
		catch (ApiException limited) {
			securityEvents.record(SecurityEventType.LOGIN_RATE_LIMITED, null);
			throw limited;
		}
		SessionService.Session session = sessions.login(request.email(), request.password());
		loginFailuresPerAccount.refund(account);
		return withSession(session);
	}

	@PostMapping("/handoff")
	@Operation(operationId = "issueHandoff",
			summary = "A one-time link that opens a session on another host without the password",
			description = "With a slug, the link opens that band (403 without access); without one, the central host. "
					+ "The link is valid for 60 seconds and carries the code in its fragment.")
	HandoffResponse issueHandoff(@Valid @RequestBody HandoffRequest request) {
		return new HandoffResponse(handoffs.issue(request.slug()));
	}

	@PostMapping("/handoff/exchange")
	@PublicEndpoint("the handoff code is the credential; the target host has no session yet")
	@Operation(operationId = "exchangeHandoff", summary = "Exchange a handoff code for a session on this host",
			description = "Sets the refresh token cookie. Any failure answers 401.")
	ResponseEntity<TokenResponse> exchangeHandoff(@Valid @RequestBody HandoffExchangeRequest request,
			HttpServletRequest http) {
		handoffExchangesPerIp.consume(clients.clientOf(http));
		return withSession(handoffs.exchange(request.code()));
	}

	@PostMapping("/refresh")
	@PublicEndpoint("authenticated by the refresh token cookie, not by an access token")
	@Operation(operationId = "refresh", summary = "Rotate the refresh token cookie and get a new access token")
	ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
		String token = RefreshCookie.read(request).orElseThrow(AuthController::sessionExpired);
		try {
			return withSession(sessions.refresh(token));
		}
		catch (SessionService.RefreshRejected rejected) {
			throw sessionExpired();
		}
	}

	@PostMapping("/logout")
	@PublicEndpoint("authenticated by the refresh token cookie; must work with an expired access token")
	@Operation(operationId = "logout", summary = "End the session of this browser on this host")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		RefreshCookie.read(request).ifPresent(sessions::logout);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookie.cleared().toString()).build();
	}

	@PostMapping("/logout-all")
	@Operation(operationId = "logoutAll", summary = "End every session of the user, on every host")
	ResponseEntity<Void> logoutAll() {
		sessions.logoutAll(CurrentUser.require().userId());
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookie.cleared().toString()).build();
	}

	private ResponseEntity<TokenResponse> withSession(SessionService.Session session) {
		long expiresIn = session.accessToken().expiresAt().getEpochSecond() - clock.instant().getEpochSecond();
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE,
					RefreshCookie.of(session.refreshToken(), session.refreshTokenMaxAge()).toString())
			.body(new TokenResponse(session.accessToken().value(), "Bearer", expiresIn));
	}

	/** 401 that also clears the cookie, so the browser stops sending a dead token. */
	private static ApiException sessionExpired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "The session has ended. Log in again.")
			.withHeader(HttpHeaders.SET_COOKIE, RefreshCookie.cleared().toString());
	}

	record LoginRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = Emails.MAX_LENGTH) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Size(max = 1024) String password) {

		@Override
		public String toString() {
			return "LoginRequest[]";
		}

	}

	record HandoffRequest(
			@Schema(description = "The band to open; omit it for the central host", example = "the-burst") @Size(max = 64) String slug) {
	}

	record HandoffResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
			example = "https://the-burst.bandcore.app/auth/handoff#code=...") String url) {

		@Override
		public String toString() {
			return "HandoffResponse[]";
		}

	}

	record HandoffExchangeRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String code) {

		@Override
		public String toString() {
			return "HandoffExchangeRequest[]";
		}

	}

	record TokenResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Bearer") String tokenType,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Seconds until the access token expires") long expiresIn) {

		@Override
		public String toString() {
			return "TokenResponse[expiresIn=" + expiresIn + "]";
		}

	}

}

package com.burstintofire.bandcore.identity.reset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.password.PasswordPolicy;
import com.burstintofire.bandcore.identity.session.SessionService;
import com.burstintofire.bandcore.identity.user.Emails;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.identity.user.UserRepository;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.SecretTokens;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Password reset by email. The request answers the same way whether or not the email has an
 * account; a completed reset ends every session of the user and sends a confirmation email.
 */
@Service
public class PasswordResetService {

	static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

	private final UserRepository users;

	private final PasswordResetTokenRepository tokens;

	private final ResetTokenGenerator generator;

	private final PasswordPolicy passwordPolicy;

	private final PasswordEncoder passwordEncoder;

	private final SessionService sessions;

	private final ApplicationEventPublisher events;

	private final IdGenerator ids;

	private final Clock clock;

	PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, ResetTokenGenerator generator,
			PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, SessionService sessions,
			ApplicationEventPublisher events, IdGenerator ids, Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.generator = generator;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.events = events;
		this.ids = ids;
		this.clock = clock;
	}

	/**
	 * Sends a reset link when the email has an active account; does nothing otherwise. The token
	 * is generated and hashed in both cases, and the email is sent after commit, off the request
	 * thread, so neither the response nor its timing reveals whether the account exists.
	 */
	@Transactional
	public void request(String rawEmail) {
		ResetTokenGenerator.Generated generated = generator.generate();
		Optional<User> found = users.findByEmail(Emails.normalize(rawEmail)).filter(User::isActive);
		if (found.isEmpty()) {
			return;
		}
		User user = found.get();
		Instant now = clock.instant();
		tokens.deletePendingFor(user.getId());
		tokens.save(new PasswordResetToken(ids.newId(), user.getId(), generated.hash(), now.plus(TOKEN_LIFETIME), now));
		events.publishEvent(new PasswordResetRequested(user.getEmail(), user.getDisplayName(), generated.token()));
	}

	/**
	 * Sets a new password. Unknown, used and expired tokens get the same error. Opening the link
	 * proves the user controls the inbox, so the email also counts as verified.
	 */
	@Transactional
	public void reset(String token, String newPassword) {
		passwordPolicy.check(newPassword);
		Instant now = clock.instant();
		PasswordResetToken found = tokens.findByTokenHash(SecretTokens.hash(token == null ? "" : token))
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(PasswordResetService::invalidToken);
		User user = users.findById(found.getUserId())
			.filter(User::isActive)
			.orElseThrow(PasswordResetService::invalidToken);
		found.markUsed(now);
		tokens.deletePendingFor(user.getId());
		user.changePasswordHash(passwordEncoder.encode(newPassword), now);
		user.markEmailVerified(now);
		sessions.logoutEverywhere(user.getId());
		events.publishEvent(new PasswordChanged(user.getEmail(), user.getDisplayName()));
	}

	private static ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.TOKEN_INVALID,
				"The link is invalid or expired. Request a new one.");
	}

}

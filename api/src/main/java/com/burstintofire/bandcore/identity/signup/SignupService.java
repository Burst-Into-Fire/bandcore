package com.burstintofire.bandcore.identity.signup;

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
import com.burstintofire.bandcore.identity.user.Emails;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.identity.user.UserRepository;
import com.burstintofire.bandcore.identity.verification.EmailVerificationToken;
import com.burstintofire.bandcore.identity.verification.EmailVerificationTokenRepository;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.security.SecretTokens;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

/**
 * Sign up and email verification. Every outcome that depends on whether an email has an
 * account produces the same response; the difference only shows up in the inbox.
 */
@Service
public class SignupService {

	static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);

	private final UserRepository users;

	private final EmailVerificationTokenRepository tokens;

	private final PasswordPolicy passwordPolicy;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	private final IdGenerator ids;

	private final Clock clock;

	SignupService(UserRepository users, EmailVerificationTokenRepository tokens, PasswordPolicy passwordPolicy,
			PasswordEncoder passwordEncoder, ApplicationEventPublisher events, IdGenerator ids, Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.ids = ids;
		this.clock = clock;
	}

	/**
	 * Creates an unverified account and sends the verification email. When the email already
	 * has an account, nothing is created: a verified owner gets a warning email, an unverified
	 * one a new verification link.
	 */
	@Transactional
	public void signUp(String rawEmail, String password, String displayName) {
		passwordPolicy.check(password);
		String email = Emails.normalize(rawEmail);
		// Hash even for existing accounts, so both paths take the same time.
		String passwordHash = passwordEncoder.encode(password);
		Optional<User> existing = users.findByEmail(email);
		if (existing.isPresent()) {
			User user = existing.get();
			if (user.isEmailVerified()) {
				events.publishEvent(new ExistingAccountSignupAttempted(user.getEmail(), user.getDisplayName()));
			}
			else if (user.isActive()) {
				issueVerification(user);
			}
			return;
		}
		User user = users.saveAndFlush(new User(ids.newId(), email, passwordHash, displayName.strip(), clock.instant()));
		issueVerification(user);
	}

	/** Marks the email as verified. Unknown, used and expired tokens get the same error. */
	@Transactional
	public void verify(String token) {
		Instant now = clock.instant();
		EmailVerificationToken found = tokens.findByTokenHash(SecretTokens.hash(token == null ? "" : token))
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(SignupService::invalidToken);
		User user = users.findById(found.getUserId()).filter(User::isActive).orElseThrow(SignupService::invalidToken);
		found.markUsed(now);
		user.markEmailVerified(now);
		tokens.deletePendingFor(user.getId());
	}

	/** Sends a new link to an unverified account; does nothing (silently) otherwise. */
	@Transactional
	public void resendVerification(String rawEmail) {
		users.findByEmail(Emails.normalize(rawEmail))
			.filter(user -> !user.isEmailVerified() && user.isActive())
			.ifPresent(this::issueVerification);
	}

	private void issueVerification(User user) {
		Instant now = clock.instant();
		tokens.deletePendingFor(user.getId());
		String token = SecretTokens.generate();
		tokens.save(new EmailVerificationToken(ids.newId(), user.getId(), SecretTokens.hash(token),
				now.plus(VERIFICATION_LIFETIME), now));
		events.publishEvent(new VerificationEmailRequested(user.getEmail(), user.getDisplayName(), token));
	}

	private static ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.TOKEN_INVALID,
				"The link is invalid or expired. Request a new one.");
	}

}

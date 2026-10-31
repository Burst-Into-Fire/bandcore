package com.burstintofire.bandcore.identity.signup;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.burstintofire.bandcore.identity.mail.MailTemplates;
import com.burstintofire.bandcore.identity.mail.Mailer;
import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.logging.LogMasking;

/**
 * Sends signup emails after the transaction commits, off the request thread, so the response
 * time does not reveal whether the email had an account. Links always point to the central host
 * and carry the token in the fragment, which browsers never send to servers.
 */
@Component
class SignupMailListener {

	private static final Logger log = LoggerFactory.getLogger(SignupMailListener.class);

	private final Mailer mailer;

	private final MailTemplates templates;

	private final PlatformProperties platform;

	SignupMailListener(Mailer mailer, MailTemplates templates, PlatformProperties platform) {
		this.mailer = mailer;
		this.templates = templates;
		this.platform = platform;
	}

	@Async
	@TransactionalEventListener
	void on(VerificationEmailRequested event) {
		String link = platform.centralUrl() + "/verify-email#token=" + event.token();
		send(templates.render("verify-email", event.email(), Map.of("name", event.displayName(), "link", link)),
				event.email());
	}

	@Async
	@TransactionalEventListener
	void on(ExistingAccountSignupAttempted event) {
		String link = platform.centralUrl() + "/login";
		send(templates.render("signup-existing-account", event.email(),
				Map.of("name", event.displayName(), "link", link)), event.email());
	}

	private void send(Mailer.OutgoingEmail email, String recipient) {
		try {
			mailer.send(email);
		}
		catch (RuntimeException ex) {
			// The token is in the body: log only the masked recipient and the error type.
			log.error("Could not send '{}' to {}: {}", email.subject(), LogMasking.email(recipient),
					ex.getClass().getSimpleName());
		}
	}

}

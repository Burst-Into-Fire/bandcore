package com.burstintofire.bandcore.identity.reset;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.burstintofire.bandcore.shared.mail.MailTemplates;
import com.burstintofire.bandcore.shared.mail.Mailer;
import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.logging.LogMasking;

/**
 * Sends reset emails after commit, off the request thread. The link points to the central host
 * and carries the token in the fragment, which browsers never send to servers.
 */
@Component
class PasswordResetMailListener {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetMailListener.class);

	private final Mailer mailer;

	private final MailTemplates templates;

	private final PlatformProperties platform;

	PasswordResetMailListener(Mailer mailer, MailTemplates templates, PlatformProperties platform) {
		this.mailer = mailer;
		this.templates = templates;
		this.platform = platform;
	}

	@Async
	@TransactionalEventListener
	void on(PasswordResetRequested event) {
		String link = platform.centralUrl() + "/reset-password#token=" + event.token();
		send(templates.render("password-reset", event.email(), Map.of("name", event.displayName(), "link", link)),
				event.email());
	}

	@Async
	@TransactionalEventListener
	void on(PasswordChanged event) {
		String link = platform.centralUrl() + "/forgot-password";
		send(templates.render("password-changed", event.email(), Map.of("name", event.displayName(), "link", link)),
				event.email());
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

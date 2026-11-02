package com.burstintofire.bandcore.tenancy.invitation;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.logging.LogMasking;
import com.burstintofire.bandcore.shared.mail.MailTemplates;
import com.burstintofire.bandcore.shared.mail.Mailer;
import com.burstintofire.bandcore.tenancy.Role;

/**
 * Sends invitation emails after commit, off the request thread. The link opens the central host
 * with the token in the fragment, which browsers never send to servers.
 */
@Component
class InvitationMailListener {

	private static final Logger log = LoggerFactory.getLogger(InvitationMailListener.class);

	private final Mailer mailer;

	private final MailTemplates templates;

	private final PlatformProperties platform;

	InvitationMailListener(Mailer mailer, MailTemplates templates, PlatformProperties platform) {
		this.mailer = mailer;
		this.templates = templates;
		this.platform = platform;
	}

	@Async
	@TransactionalEventListener
	void on(InvitationEmailRequested event) {
		String link = platform.centralUrl() + "/invitations/accept#token=" + event.token();
		Mailer.OutgoingEmail email = templates.render("invitation", event.email(), Map.of("band", event.bandName(),
				"inviter", event.inviterName(), "role", roleLabel(event.role()), "link", link));
		try {
			mailer.send(email);
		}
		catch (RuntimeException ex) {
			// The token is in the body: log only the masked recipient and the error type.
			log.error("Could not send '{}' to {}: {}", email.subject(), LogMasking.email(event.email()),
					ex.getClass().getSimpleName());
		}
	}

	static String roleLabel(Role role) {
		return switch (role) {
			case ADMIN -> "administrador";
			case MEMBER -> "integrante";
			case READ_ONLY -> "somente leitura";
		};
	}

}

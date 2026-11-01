package com.burstintofire.bandcore.identity.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP adapter: Mailpit locally, the provider's SMTP relay in production. */
@Component
class SmtpMailer implements Mailer {

	private final JavaMailSender sender;

	private final String from;

	SmtpMailer(JavaMailSender sender, @Value("${bandcore.mail.from}") String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public void send(OutgoingEmail email) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email.to());
		message.setSubject(email.subject());
		message.setText(email.body());
		sender.send(message);
	}

}

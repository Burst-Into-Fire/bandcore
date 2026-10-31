package com.burstintofire.bandcore.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * In-process SMTP server (GreenMail) for the whole test run. The application sends emails to it
 * asynchronously after commit, so tests wait for them with {@link #awaitEmail}.
 */
public final class TestMailServer {

	private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

	private static final GreenMail SERVER = start();

	public record Email(String to, String subject, String body) {

		/** The token of a link such as {@code .../verify-email#token=...}. */
		public String token() {
			Matcher matcher = TOKEN.matcher(body);
			if (!matcher.find()) {
				throw new AssertionError("No token in email body:\n" + body);
			}
			return matcher.group(1);
		}

	}

	private TestMailServer() {
	}

	private static GreenMail start() {
		GreenMail server = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
		server.start();
		return server;
	}

	public static void register(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> "127.0.0.1");
		registry.add("spring.mail.port", () -> SERVER.getSmtp().getPort());
	}

	public static void purge() {
		try {
			SERVER.purgeEmailFromAllMailboxes();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Waits until an email for {@code to} arrives, and returns the latest one. */
	public static Email awaitEmail(String to) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			List<Email> emails = emailsTo(to);
			if (!emails.isEmpty()) {
				return emails.getLast();
			}
			sleep(50);
		}
		throw new AssertionError("No email for " + to);
	}

	/** Every email received so far for {@code to}, oldest first. */
	public static List<Email> emailsTo(String to) {
		List<Email> emails = new ArrayList<>();
		for (Message message : SERVER.getReceivedMessages()) {
			try {
				boolean matches = Arrays.stream(message.getAllRecipients())
					.map(address -> ((InternetAddress) address).getAddress())
					.anyMatch(to::equalsIgnoreCase);
				if (matches) {
					emails.add(new Email(to, message.getSubject(), String.valueOf(message.getContent())));
				}
			}
			catch (MessagingException | java.io.IOException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return emails;
	}

	/** Gives asynchronous senders time to run before asserting that nothing was sent. */
	public static void settle() {
		sleep(500);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}

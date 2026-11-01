package com.burstintofire.bandcore.identity.mail;

/** Sends transactional emails. The SMTP adapter is the only implementation for now. */
public interface Mailer {

	void send(OutgoingEmail email);

	record OutgoingEmail(String to, String subject, String body) {

		@Override
		public String toString() {
			// Never print the recipient or the body (it may contain a token).
			return "OutgoingEmail[subject=" + subject + "]";
		}

	}

}

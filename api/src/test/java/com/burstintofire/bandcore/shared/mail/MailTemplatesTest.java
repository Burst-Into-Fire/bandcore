package com.burstintofire.bandcore.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MailTemplatesTest {

	@Test
	void valuesCannotAddLinesOrHeaders() {
		Mailer.OutgoingEmail email = new MailTemplates().render("verify-email", "ana@example.com",
				Map.of("name", "Ana\r\nBcc: evil@example.com\n\nClique aqui: http://evil.example", "link", "http://ok"));

		assertThat(email.body()).contains("Olá, Ana  Bcc: evil@example.com  Clique aqui: http://evil.example!")
			.doesNotContain("\nBcc:");
	}

	@Test
	void plainValuesAreKeptAsTheyAre() {
		assertThat(MailTemplates.singleLine("  The <b>Burst</b> & Co  ")).isEqualTo("The <b>Burst</b> & Co");
	}

}

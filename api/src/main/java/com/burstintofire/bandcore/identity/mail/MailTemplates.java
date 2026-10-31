package com.burstintofire.bandcore.identity.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Plain-text pt-BR templates in {@code mail/pt-BR/{name}.txt}. The first line is
 * {@code Subject: ...}; {@code {{key}}} placeholders are replaced by the given values. Plain
 * text means user-supplied values (names) can never inject markup.
 */
@Component
public class MailTemplates {

	private static final String SUBJECT_PREFIX = "Subject: ";

	private final Map<String, String> cache = new ConcurrentHashMap<>();

	public Mailer.OutgoingEmail render(String name, String to, Map<String, String> values) {
		String template = cache.computeIfAbsent(name, MailTemplates::load);
		int firstLineEnd = template.indexOf('\n');
		String subject = template.substring(SUBJECT_PREFIX.length(), firstLineEnd).strip();
		String body = template.substring(firstLineEnd + 1).stripLeading();
		for (Map.Entry<String, String> value : values.entrySet()) {
			subject = subject.replace("{{" + value.getKey() + "}}", value.getValue());
			body = body.replace("{{" + value.getKey() + "}}", value.getValue());
		}
		return new Mailer.OutgoingEmail(to, subject, body);
	}

	private static String load(String name) {
		String path = "mail/pt-BR/" + name + ".txt";
		try {
			String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
			if (!template.startsWith(SUBJECT_PREFIX)) {
				throw new IllegalStateException(path + " must start with '" + SUBJECT_PREFIX + "'");
			}
			return template;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Missing email template " + path, ex);
		}
	}

}

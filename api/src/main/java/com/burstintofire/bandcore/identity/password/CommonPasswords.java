package com.burstintofire.bandcore.identity.password;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;

/** Common passwords long enough to pass the length rule, bundled in security/common-passwords.txt. */
final class CommonPasswords {

	static final String RESOURCE = "security/common-passwords.txt";

	private final Set<String> passwords;

	private CommonPasswords(Set<String> passwords) {
		this.passwords = passwords;
	}

	static CommonPasswords load() {
		Set<String> passwords = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isEmpty() && !line.startsWith("# ")) {
					passwords.add(line);
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot read " + RESOURCE, ex);
		}
		return new CommonPasswords(Set.copyOf(passwords));
	}

	boolean contains(String password) {
		return passwords.contains(password.toLowerCase(Locale.ROOT));
	}

	int size() {
		return passwords.size();
	}

}

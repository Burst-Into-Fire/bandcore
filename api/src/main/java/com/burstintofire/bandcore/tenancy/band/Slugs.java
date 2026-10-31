package com.burstintofire.bandcore.tenancy.band;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for band slugs. A slug becomes the band's subdomain ({@code {slug}.bandcore.app}), so
 * it must be a valid DNS label and must not collide with platform hosts.
 */
public final class Slugs {

	public static final int MIN_LENGTH = 3;

	public static final int MAX_LENGTH = 32;

	/** Lowercase letters and digits, separated by single hyphens; no leading or trailing hyphen. */
	private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	/**
	 * Hosts the platform uses or may use. Never remove an entry once a band could have taken it;
	 * adding entries is safe for new bands only, so check existing bands first.
	 */
	static final Set<String> RESERVED = Set.of("about", "account", "accounts", "admin", "administrator", "api",
			"app", "apps", "assets", "auth", "billing", "blog", "cdn", "dashboard", "demo", "dev", "developer",
			"developers", "docs", "download", "email", "files", "ftp", "help", "home", "imap", "img", "images",
			"internal", "legal", "localhost", "login", "logout", "mail", "media", "mx", "news", "ns1", "ns2",
			"oauth", "pop", "pop3", "portal", "preview", "privacy", "prod", "production", "root", "sandbox",
			"security", "settings", "signin", "signup", "smtp", "sso", "staff", "staging", "static", "stats",
			"status", "store", "support", "system", "terms", "test", "uploads", "webmail", "www");

	public enum Problem {

		/** Wrong length, characters or hyphen placement. */
		INVALID,
		/** Valid but reserved for the platform. */
		RESERVED

	}

	private Slugs() {
	}

	/** Trims and lowercases user input; validation runs on the result. */
	public static String normalize(String input) {
		return input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
	}

	/** The problem with a normalized slug, or empty when it can be used. */
	public static Optional<Problem> check(String slug) {
		if (slug == null || slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH || !FORMAT.matcher(slug).matches()
				|| slug.startsWith("xn--")) {
			return Optional.of(Problem.INVALID);
		}
		if (RESERVED.contains(slug)) {
			return Optional.of(Problem.RESERVED);
		}
		return Optional.empty();
	}

	/** Whether a host label could be a band slug, used to reject malformed hosts early. */
	public static boolean isWellFormed(String label) {
		return check(label).filter(problem -> problem == Problem.INVALID).isEmpty();
	}

}

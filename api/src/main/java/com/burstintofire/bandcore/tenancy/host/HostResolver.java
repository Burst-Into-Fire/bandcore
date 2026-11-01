package com.burstintofire.bandcore.tenancy.host;

import java.util.Locale;
import java.util.regex.Pattern;

import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.tenancy.band.Slugs;

/** Turns a raw {@code Host} header into the central host, a band slug or nothing. */
public final class HostResolver {

	public sealed interface Resolved permits Central, Band, Unknown {

	}

	public record Central(String host) implements Resolved {
	}

	public record Band(String host, String slug) implements Resolved {
	}

	public record Unknown() implements Resolved {
	}

	private static final Pattern HOST_CHARACTERS = Pattern.compile("[a-z0-9.-]{1,253}");

	private HostResolver() {
	}

	/** Lowercase, without port and trailing dot; null when the value cannot be a host name. */
	public static String normalize(String rawHost) {
		if (rawHost == null) {
			return null;
		}
		String host = rawHost.strip().toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon >= 0) {
			String port = host.substring(colon + 1);
			if (!port.chars().allMatch(Character::isDigit)) {
				return null;
			}
			host = host.substring(0, colon);
		}
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return HOST_CHARACTERS.matcher(host).matches() ? host : null;
	}

	public static Resolved resolve(String rawHost, PlatformProperties platform) {
		String host = normalize(rawHost);
		if (host == null) {
			return new Unknown();
		}
		if (host.equals(platform.centralHost())) {
			return new Central(host);
		}
		String suffix = "." + platform.baseDomain();
		if (!host.endsWith(suffix)) {
			return new Unknown();
		}
		String label = host.substring(0, host.length() - suffix.length());
		if (label.contains(".") || Slugs.check(label).isPresent()) {
			return new Unknown();
		}
		return new Band(host, label);
	}

}

package com.burstintofire.bandcore.shared.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.config.PlatformProperties;

/**
 * Who is calling. Forwarded headers are believed only from the trusted proxies configured in
 * {@code bandcore.platform.trusted-proxies} (addresses or CIDR ranges); anyone else could put any
 * value in them.
 */
@Component
public class ClientAddresses {

	static final String FORWARDED_FOR = "X-Forwarded-For";

	private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

	private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

	private final List<IpAddressMatcher> trustedProxies;

	public ClientAddresses(PlatformProperties platform) {
		this.trustedProxies = platform.trustedProxies()
			.stream()
			.map(String::strip)
			.filter(proxy -> !proxy.isEmpty())
			.map(IpAddressMatcher::new)
			.toList();
	}

	public boolean isTrustedProxy(String address) {
		return isIpLiteral(address) && trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
	}

	/**
	 * The client's address: the remote address, or, when it is a trusted proxy, the right-most
	 * {@code X-Forwarded-For} entry that is not a trusted proxy. Entries to the left of it were
	 * written by the client and are ignored.
	 */
	public String clientOf(HttpServletRequest request) {
		String address = request.getRemoteAddr();
		if (!isTrustedProxy(address)) {
			return address;
		}
		List<String> hops = forwardedFor(request);
		Collections.reverse(hops);
		for (String hop : hops) {
			if (!isIpLiteral(hop)) {
				// Garbage written by the client: the last trusted hop is the best we know.
				return address;
			}
			if (!isTrustedProxy(hop)) {
				return hop;
			}
			address = hop;
		}
		return address;
	}

	private static List<String> forwardedFor(HttpServletRequest request) {
		List<String> hops = new ArrayList<>();
		var headers = request.getHeaders(FORWARDED_FOR);
		while (headers.hasMoreElements()) {
			for (String hop : headers.nextElement().split(",")) {
				if (!hop.isBlank()) {
					hops.add(hop.strip());
				}
			}
		}
		return hops;
	}

	/** Only literals: matching a host name would trigger a DNS lookup for attacker input. */
	static boolean isIpLiteral(String value) {
		return value != null && value.length() <= 45 && (IPV4.matcher(value).matches() || IPV6.matcher(value).matches());
	}

}

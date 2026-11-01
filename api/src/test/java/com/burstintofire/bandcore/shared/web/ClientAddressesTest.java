package com.burstintofire.bandcore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.burstintofire.bandcore.shared.config.PlatformProperties;

class ClientAddressesTest {

	private final ClientAddresses clients = new ClientAddresses(
			new PlatformProperties("localhost", "http", null, List.of("10.0.0.5", "172.16.0.0/12")));

	@Test
	void anUntrustedCallerIsTheClientWhateverItsHeadersSay() {
		assertThat(clients.clientOf(request("203.0.113.7", "198.51.100.1"))).isEqualTo("203.0.113.7");
	}

	@Test
	void behindATrustedProxyTheClientIsTheRightMostUntrustedHop() {
		assertThat(clients.clientOf(request("10.0.0.5", "203.0.113.7"))).isEqualTo("203.0.113.7");
		assertThat(clients.clientOf(request("10.0.0.5", "198.51.100.1, 203.0.113.7"))).isEqualTo("203.0.113.7");
		assertThat(clients.clientOf(request("10.0.0.5", "203.0.113.7, 172.20.0.3"))).isEqualTo("203.0.113.7");
	}

	@Test
	void cidrRangesAreTrusted() {
		assertThat(clients.isTrustedProxy("172.18.0.2")).isTrue();
		assertThat(clients.isTrustedProxy("172.32.0.1")).isFalse();
		assertThat(clients.clientOf(request("172.18.0.2", "203.0.113.7"))).isEqualTo("203.0.113.7");
	}

	@Test
	void withoutForwardedForTheProxyIsTheClient() {
		assertThat(clients.clientOf(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
	}

	@Test
	void garbageInTheHeaderNeverBecomesTheClient() {
		assertThat(clients.clientOf(request("10.0.0.5", "evil.example.com"))).isEqualTo("10.0.0.5");
		assertThat(clients.clientOf(request("10.0.0.5", "203.0.113.7, not-an-ip"))).isEqualTo("10.0.0.5");
	}

	@Test
	void onlyIpLiteralsAreMatched() {
		assertThat(ClientAddresses.isIpLiteral("203.0.113.7")).isTrue();
		assertThat(ClientAddresses.isIpLiteral("2001:db8::1")).isTrue();
		assertThat(ClientAddresses.isIpLiteral("cafe.example")).isFalse();
		assertThat(clients.isTrustedProxy("localhost")).isFalse();
	}

	private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddress);
		if (forwardedFor != null) {
			request.addHeader(ClientAddresses.FORWARDED_FOR, forwardedFor);
		}
		return request;
	}

}

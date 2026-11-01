package com.burstintofire.bandcore.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

class SecurityHeadersFilterTest {

	@Test
	void hstsOnlyWhenEnabled() throws Exception {
		assertThat(headersWith(true).getHeader("Strict-Transport-Security"))
			.isEqualTo("max-age=31536000; includeSubDomains");
		assertThat(headersWith(false).containsHeader("Strict-Transport-Security")).isFalse();
	}

	private static MockHttpServletResponse headersWith(boolean hsts) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		new SecurityHeadersFilter(new HttpProperties(hsts, DataSize.ofMegabytes(1)))
			.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
		return response;
	}

}

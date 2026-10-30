package com.burstintofire.bandcore.tenancy.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.burstintofire.bandcore.shared.config.PlatformProperties;

class HostResolverTest {

	private static final PlatformProperties LOCAL = new PlatformProperties("localhost", "http", 3000, List.of());

	private static final PlatformProperties PRODUCTION = new PlatformProperties("bandcore.app", "https", null,
			List.of());

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(nullValues = "NULL", textBlock = """
			burst.localhost,          burst.localhost
			BURST.LocalHost,          burst.localhost
			burst.localhost:3000,     burst.localhost
			burst.localhost.,         burst.localhost
			'  burst.localhost  ',    burst.localhost
			burst.localhost:abc,      NULL
			'burst .localhost',       NULL
			burst_x.localhost,        NULL
			[::1]:8080,               NULL
			'',                       NULL
			""")
	void normalizesHosts(String raw, String expected) {
		assertThat(HostResolver.normalize(raw)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(textBlock = """
			app.localhost,            central
			APP.localhost:3000,       central
			burst.localhost,          band burst
			the-burst.localhost.,     band the-burst
			localhost,                unknown
			a.b.localhost,            unknown
			-burst.localhost,         unknown
			bu.localhost,             unknown
			api.localhost,            unknown
			www.localhost,            unknown
			burst.example.com,        unknown
			burstlocalhost,           unknown
			""")
	void resolvesLocalHosts(String raw, String expected) {
		assertThat(describe(HostResolver.resolve(raw, LOCAL))).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(textBlock = """
			app.bandcore.app,         central
			burst.bandcore.app,       band burst
			bandcore.app,             unknown
			burst.localhost,          unknown
			burst.bandcore.app.evil,  unknown
			""")
	void resolvesProductionHosts(String raw, String expected) {
		assertThat(describe(HostResolver.resolve(raw, PRODUCTION))).isEqualTo(expected);
	}

	private static String describe(HostResolver.Resolved resolved) {
		return switch (resolved) {
			case HostResolver.Central central -> "central";
			case HostResolver.Band band -> "band " + band.slug();
			case HostResolver.Unknown unknown -> "unknown";
		};
	}

}

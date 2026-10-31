package com.burstintofire.bandcore.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import com.burstintofire.bandcore.BandcoreApplication;

class ModularityTest {

	private final ApplicationModules modules = ApplicationModules.of(BandcoreApplication.class);

	@Test
	void modulesRespectTheirBoundaries() {
		modules.verify();
	}

	/** Writes module diagrams and a module canvas to target/spring-modulith-docs. */
	@Test
	void writeModuleDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}

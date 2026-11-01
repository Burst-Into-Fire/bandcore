package com.burstintofire.bandcore.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** The only way to create primary keys: application-generated UUIDv7 (ADR-0007). */
@Component
public class IdGenerator {

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public IdGenerator(Clock clock) {
		this.clock = clock;
	}

	public UUID newId() {
		return UuidV7.from(clock.millis(), random);
	}

}

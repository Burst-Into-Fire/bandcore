package com.burstintofire.bandcore.shared.id;

import java.util.Random;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): a 48-bit Unix timestamp in milliseconds followed by random bits,
 * so new keys are mostly sequential in B-tree indexes and still unguessable (ADR-0007).
 */
public final class UuidV7 {

	private static final long MAX_TIMESTAMP = (1L << 48) - 1;

	private UuidV7() {
	}

	public static UUID from(long epochMillis, Random random) {
		if (epochMillis < 0 || epochMillis > MAX_TIMESTAMP) {
			throw new IllegalArgumentException("Timestamp out of the UUIDv7 range: " + epochMillis);
		}
		long randA = random.nextLong() & 0xFFFL;
		long mostSignificant = (epochMillis << 16) | (0x7L << 12) | randA;
		long randB = random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
		long leastSignificant = (0x2L << 62) | randB;
		return new UUID(mostSignificant, leastSignificant);
	}

	/** Milliseconds since the epoch stored in a version 7 UUID. */
	public static long timestamp(UUID uuid) {
		if (uuid.version() != 7) {
			throw new IllegalArgumentException("Not a version 7 UUID: " + uuid);
		}
		return uuid.getMostSignificantBits() >>> 16;
	}

}

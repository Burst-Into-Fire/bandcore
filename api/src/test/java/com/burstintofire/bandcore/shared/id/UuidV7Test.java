package com.burstintofire.bandcore.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class UuidV7Test {

	@Property
	void hasVersionSevenVariantTwoAndTheTimestamp(@ForAll @LongRange(min = 0, max = (1L << 48) - 1) long millis,
			@ForAll long seed) {
		UUID uuid = UuidV7.from(millis, new Random(seed));

		assertThat(uuid.version()).isEqualTo(7);
		assertThat(uuid.variant()).isEqualTo(2);
		assertThat(UuidV7.timestamp(uuid)).isEqualTo(millis);
	}

	@Test
	void laterTimestampsSortAfterEarlierOnes() {
		SecureRandom random = new SecureRandom();
		UUID earlier = UuidV7.from(1_700_000_000_000L, random);
		UUID later = UuidV7.from(1_700_000_000_001L, random);

		assertThat(later.toString()).isGreaterThan(earlier.toString());
	}

	@Test
	void idsInTheSameMillisecondAreUnique() {
		SecureRandom random = new SecureRandom();
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			ids.add(UuidV7.from(1_700_000_000_000L, random));
		}

		assertThat(ids).hasSize(10_000);
	}

	@Test
	void rejectsTimestampsOutOfRange() {
		assertThatThrownBy(() -> UuidV7.from(-1, new Random())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> UuidV7.from(1L << 48, new Random())).isInstanceOf(IllegalArgumentException.class);
	}

}

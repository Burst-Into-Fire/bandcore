package com.burstintofire.bandcore.shared.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the shared vectors in {@code contracts/money/allocation.json}. The web app runs the same
 * file ({@code web/src/lib/allocation.test.ts}), so both sides agree on every cent.
 */
class AllocationVectorsTest {

	record EqualCase(String name, long totalCents, int parts, long[] expected) {

		@Override
		public String toString() {
			return name;
		}

	}

	record WeightsCase(String name, long totalCents, long[] weights, long[] expected) {

		@Override
		public String toString() {
			return name;
		}

	}

	record Vectors(String description, List<EqualCase> equal, List<WeightsCase> byWeights) {
	}

	/** Maven runs the tests from {@code api/}. */
	static final Path FILE = Path.of("..", "contracts", "money", "allocation.json");

	static Vectors vectors() {
		return JsonMapper.builder().build().readValue(FILE.toFile(), Vectors.class);
	}

	static Stream<EqualCase> equalCases() {
		return vectors().equal().stream();
	}

	static Stream<WeightsCase> weightCases() {
		return vectors().byWeights().stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("equalCases")
	void equalParts(EqualCase vector) {
		assertThat(Allocation.equal(vector.totalCents(), vector.parts())).containsExactly(vector.expected());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("weightCases")
	void weightedParts(WeightsCase vector) {
		assertThat(Allocation.byWeights(vector.totalCents(), vector.weights())).containsExactly(vector.expected());
	}

}

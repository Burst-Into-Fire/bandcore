package com.burstintofire.bandcore.tenancy.band;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(nullValues = "OK", textBlock = """
			burst,                              OK
			the-burst,                          OK
			a1b,                                OK
			123,                                OK
			b-2-c,                              OK
			abcdefghijklmnopqrstuvwxyz012345,   OK
			ab,                                 INVALID
			abcdefghijklmnopqrstuvwxyz0123456,  INVALID
			-burst,                             INVALID
			burst-,                             INVALID
			the--burst,                         INVALID
			the_burst,                          INVALID
			the.burst,                          INVALID
			Burst,                              INVALID
			'the burst',                        INVALID
			bürst,                              INVALID
			xn--brst-kva,                       INVALID
			app,                                RESERVED
			api,                                RESERVED
			www,                                RESERVED
			admin,                              RESERVED
			staging,                            RESERVED
			login,                              RESERVED
			""")
	void checksTheRules(String slug, Slugs.Problem expected) {
		assertThat(Slugs.check(slug)).isEqualTo(Optional.ofNullable(expected));
	}

	@ParameterizedTest
	@CsvSource({ "'  The-Burst ', the-burst", "BURST, burst" })
	void normalizesCaseAndSurroundingSpaces(String input, String expected) {
		assertThat(Slugs.normalize(input)).isEqualTo(expected);
	}

	@Property
	void generatedValidSlugsAreAccepted(@ForAll("validSlugs") String slug) {
		Assume.that(!Slugs.RESERVED.contains(slug));

		assertThat(Slugs.check(slug)).isEmpty();
	}

	@Property
	void anyCharacterOutsideTheAlphabetIsRejected(@ForAll("validSlugs") String slug,
			@ForAll("forbiddenCharacters") char other) {
		String withBadCharacter = slug.charAt(0) + String.valueOf(other) + slug.substring(1);

		assertThat(Slugs.check(withBadCharacter)).contains(Slugs.Problem.INVALID);
	}

	@Provide
	Arbitrary<Character> forbiddenCharacters() {
		return Arbitraries.chars().all().filter(c -> !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '-');
	}

	@Provide
	Arbitrary<String> validSlugs() {
		Arbitrary<String> part = Arbitraries.strings().withCharRange('a', 'z').withCharRange('0', '9').ofMinLength(1)
			.ofMaxLength(10);
		return Combinators.combine(part, part.list().ofMaxSize(2))
			.as((first, rest) -> first + (rest.isEmpty() ? "" : "-" + String.join("-", rest)))
			.filter(slug -> slug.length() >= Slugs.MIN_LENGTH && slug.length() <= Slugs.MAX_LENGTH);
	}

}

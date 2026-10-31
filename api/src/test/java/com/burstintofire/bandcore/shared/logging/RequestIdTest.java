package com.burstintofire.bandcore.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.Chars;
import net.jqwik.api.constraints.StringLength;

class RequestIdTest {

	@Property
	void resultIsAlwaysSafeToLogAndEcho(@ForAll String incoming) {
		String resolved = RequestId.resolve(incoming);

		assertThat(resolved).matches("[A-Za-z0-9._-]{1,64}");
	}

	@Property
	void safeIdsArePropagatedUnchanged(@ForAll @StringLength(min = 1, max = 64) @CharRange(from = 'a', to = 'z')
			@CharRange(from = 'A', to = 'Z') @CharRange(from = '0', to = '9') @Chars({ '.', '_', '-' }) String incoming) {
		assertThat(RequestId.resolve(incoming)).isEqualTo(incoming);
	}

	@Property
	void tooLongIdsAreReplaced(@ForAll @StringLength(min = 65, max = 200) @CharRange(from = 'a', to = 'z') String incoming) {
		assertThat(RequestId.resolve(incoming)).isNotEqualTo(incoming);
	}

}

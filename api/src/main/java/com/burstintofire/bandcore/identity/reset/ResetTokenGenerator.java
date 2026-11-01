package com.burstintofire.bandcore.identity.reset;

import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.security.SecretTokens;

/**
 * Generates a 256-bit token and its hash. A bean (not a static call) so tests can prove that
 * a request for an unknown email does the same work as one for a real account.
 */
@Component
class ResetTokenGenerator {

	record Generated(String token, byte[] hash) {

		@Override
		public String toString() {
			return "Generated[]";
		}

	}

	Generated generate() {
		String token = SecretTokens.generate();
		return new Generated(token, SecretTokens.hash(token));
	}

}

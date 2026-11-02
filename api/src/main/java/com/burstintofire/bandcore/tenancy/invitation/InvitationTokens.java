package com.burstintofire.bandcore.tenancy.invitation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.security.SecretTokens;

/**
 * The global {@code invitation_tokens} table: from a token's hash to its invitation and band,
 * before any band is known (the acceptance happens on the central host). Plain SQL, because it
 * is not band data and tenant packages only have band-filtered repositories.
 */
@Component
class InvitationTokens {

	record Lookup(UUID invitationId, UUID bandId) {
	}

	private final JdbcTemplate jdbc;

	InvitationTokens(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A new token for the invitation, replacing any previous one. Returns the raw token. */
	String issue(Invitation invitation) {
		String token = SecretTokens.generate();
		jdbc.update("DELETE FROM invitation_tokens WHERE invitation_id = ?", invitation.getId());
		jdbc.update("INSERT INTO invitation_tokens (invitation_id, band_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
				invitation.getId(), invitation.getBandId(), SecretTokens.hash(token),
				Timestamp.from(invitation.getExpiresAt()));
		return token;
	}

	/** The invitation of a token that has not expired; empty for anything else. */
	Optional<Lookup> find(String token, Instant now) {
		return jdbc
			.query("SELECT invitation_id, band_id FROM invitation_tokens WHERE token_hash = ? AND expires_at > ?",
					(rs, row) -> new Lookup(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
					SecretTokens.hash(token == null ? "" : token), Timestamp.from(now))
			.stream()
			.findFirst();
	}

	/** After acceptance or revocation: the token stops working. */
	void delete(UUID invitationId) {
		jdbc.update("DELETE FROM invitation_tokens WHERE invitation_id = ?", invitationId);
	}

}

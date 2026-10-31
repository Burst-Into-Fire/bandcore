package com.burstintofire.bandcore.database;

import java.util.UUID;

/** SQL for test rows, inserted through OwnerSql. */
final class TestRows {

	private TestRows() {
	}

	static String user(UUID id) {
		return """
				INSERT INTO users (id, email, password_hash, display_name, email_verified_at, created_at, updated_at)
				VALUES ('%s', '%s@example.com', '{noop}unused', 'User', now(), now(), now())
				""".formatted(id, id);
	}

	static String band(UUID id, String slug) {
		return """
				INSERT INTO bands (id, slug, name, created_at, updated_at)
				VALUES ('%s', '%s', '%s', now(), now())
				""".formatted(id, slug, slug);
	}

	static String membership(UUID bandId, UUID userId) {
		return """
				INSERT INTO memberships (id, band_id, user_id, role, created_at, updated_at)
				VALUES (gen_random_uuid(), '%s', '%s', 'MEMBER', now(), now())
				""".formatted(bandId, userId);
	}

	static String member(UUID bandId, String name, UUID userId) {
		return """
				INSERT INTO members (id, band_id, display_name, user_id, joined_at, created_at, updated_at)
				VALUES (gen_random_uuid(), '%s', '%s', %s, now(), now(), now())
				""".formatted(bandId, name, userId == null ? "NULL" : "'" + userId + "'");
	}

}

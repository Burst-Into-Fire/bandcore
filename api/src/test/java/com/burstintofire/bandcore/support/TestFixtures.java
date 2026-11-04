package com.burstintofire.bandcore.support;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test data builders for accounts, bands and memberships, written straight to the database as
 * the owner (inside each band's RLS context), plus a login helper that goes through the API.
 * Registered by {@link IntegrationTest} with {@code @Import}.
 */
public class TestFixtures {

	public static final String PASSWORD = "a long and unique passphrase";

	private final PasswordEncoder passwordEncoder;

	/** Argon2id is slow on purpose; hash each test password once per run. */
	private final Map<String, String> hashes = new ConcurrentHashMap<>();

	public TestFixtures(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	public UUID verifiedUser(String email) {
		return user(email, PASSWORD, true);
	}

	public UUID user(String email, String password, boolean verified) {
		UUID id = UUID.randomUUID();
		String hash = hashes.computeIfAbsent(password, passwordEncoder::encode);
		OwnerSql.global("""
				INSERT INTO bandcore.users (id, email, password_hash, display_name, email_verified_at, created_at, updated_at)
				VALUES ('%s', '%s', '%s', '%s', %s, now(), now())
				""".formatted(id, email, hash, displayName(email), verified ? "now()" : "NULL"));
		return id;
	}

	public UUID band(String slug) {
		return band(slug, Character.toUpperCase(slug.charAt(0)) + slug.substring(1));
	}

	public UUID band(String slug, String name) {
		UUID id = UUID.randomUUID();
		OwnerSql.global("""
				INSERT INTO bandcore.bands (id, slug, name, created_at, updated_at)
				VALUES ('%s', '%s', '%s', now(), now())
				""".formatted(id, slug, name));
		return id;
	}

	/** Active membership with a role, plus the linked member (integrante). Returns the membership id. */
	public UUID membership(UUID bandId, UUID userId, String role) {
		UUID id = membershipOnly(bandId, userId, role);
		OwnerSql.inBand(bandId, """
				INSERT INTO bandcore.members (id, band_id, display_name, user_id, joined_at, created_at, updated_at)
				SELECT gen_random_uuid(), '%s', display_name, id, now(), now(), now() FROM bandcore.users WHERE id = '%s'
				""".formatted(bandId, userId));
		return id;
	}

	/** Active membership with a role and no member record. Returns the membership id. */
	public UUID membershipOnly(UUID bandId, UUID userId, String role) {
		UUID id = UUID.randomUUID();
		OwnerSql.inBand(bandId, """
				INSERT INTO bandcore.memberships (id, band_id, user_id, role, created_at, updated_at)
				VALUES ('%s', '%s', '%s', '%s', now(), now())
				""".formatted(id, bandId, userId, role));
		return id;
	}

	/** A pending invitation valid for 7 days, and its raw token. */
	public record CreatedInvitation(UUID id, String token) {
	}

	public CreatedInvitation invitation(UUID bandId, String email, String role, UUID invitedBy) {
		UUID id = UUID.randomUUID();
		String token = UUID.randomUUID().toString().replace("-", "") + "abcdefghijk";
		OwnerSql.inBand(bandId, """
				INSERT INTO bandcore.invitations (id, band_id, email, role, expires_at, invited_by, created_at, updated_at)
				VALUES ('%s', '%s', '%s', '%s', now() + interval '7 days', '%s', now(), now())
				""".formatted(id, bandId, email, role, invitedBy), """
				INSERT INTO bandcore.invitation_tokens (invitation_id, band_id, token_hash, expires_at)
				VALUES ('%s', '%s', sha256(convert_to('%s', 'UTF8')), now() + interval '7 days')
				""".formatted(id, bandId, token));
		return new CreatedInvitation(id, token);
	}

	public UUID member(UUID bandId, String name) {
		UUID id = UUID.randomUUID();
		OwnerSql.inBand(bandId, """
				INSERT INTO bandcore.members (id, band_id, display_name, joined_at, created_at, updated_at)
				VALUES ('%s', '%s', '%s', now(), now(), now())
				""".formatted(id, bandId, name));
		return id;
	}

	/** An active expense category. */
	public UUID category(UUID bandId, String name) {
		UUID id = UUID.randomUUID();
		OwnerSql.inBand(bandId, """
				INSERT INTO bandcore.expense_categories (id, band_id, name, created_at, updated_at)
				VALUES ('%s', '%s', '%s', now(), now())
				""".formatted(id, bandId, name));
		return id;
	}

	/**
	 * A planned expense of R$ 100,00 dated 2026-11-20, created by {@code createdBy} and charged
	 * entirely to the band's first member with an account (members without one stay free of
	 * history, so tests can delete them). One statement, so the deferred check that shares add up
	 * to the amount sees both rows.
	 */
	public UUID plannedExpense(UUID bandId, UUID categoryId, String description, UUID createdBy) {
		UUID id = UUID.randomUUID();
		OwnerSql.inBand(bandId, """
				WITH expense AS (
				    INSERT INTO bandcore.expenses (id, band_id, description, category_id, currency, amount_cents,
				        expense_date, status, split_mode, created_by, created_at, updated_at)
				    VALUES ('%s', '%s', '%s', '%s', 'BRL', 10000, DATE '2026-11-20', 'PLANNED', 'EQUAL', '%s', now(), now())
				    RETURNING id, band_id)
				INSERT INTO bandcore.expense_splits (id, band_id, expense_id, member_id, share_cents)
				SELECT gen_random_uuid(), e.band_id, e.id,
				    (SELECT m.id FROM bandcore.members m WHERE m.band_id = e.band_id AND m.user_id IS NOT NULL
				     ORDER BY m.id LIMIT 1), 10000
				FROM expense e
				""".formatted(id, bandId, description, categoryId, createdBy));
		return id;
	}

	/** Like {@link #plannedExpense}, but paid on 2026-11-20 by the same member. */
	public UUID paidExpense(UUID bandId, UUID categoryId, String description, UUID createdBy) {
		UUID id = plannedExpense(bandId, categoryId, description, createdBy);
		OwnerSql.inBand(bandId, """
				UPDATE bandcore.expenses SET status = 'PAID', paid_on = DATE '2026-11-20',
				    paid_by_member_id = (SELECT m.member_id FROM bandcore.expense_splits m WHERE m.expense_id = '%s')
				WHERE id = '%s'
				""".formatted(id, id));
		return id;
	}

	/** A settlement of R$ 50,00 paid on 2026-11-20, recorded by {@code createdBy}. */
	public UUID settlement(UUID bandId, UUID fromMemberId, UUID toMemberId, String note, UUID createdBy) {
		UUID id = UUID.randomUUID();
		OwnerSql.inBand(bandId, """
				INSERT INTO bandcore.settlements (id, band_id, from_member_id, to_member_id, currency, amount_cents,
				    paid_on, note, created_by, created_at, updated_at)
				VALUES ('%s', '%s', '%s', '%s', 'BRL', 5000, DATE '2026-11-20', '%s', '%s', now(), now())
				""".formatted(id, bandId, fromMemberId, toMemberId, note, createdBy));
		return id;
	}

	/** The member (integrante) linked to an account in a band. */
	public UUID memberOf(UUID bandId, UUID userId) {
		return UUID.fromString(OwnerSql.superuserQueryForString(
				"SELECT id FROM bandcore.members WHERE band_id = '%s' AND user_id = '%s'".formatted(bandId, userId)));
	}

	/** Logs in through the API on the client's host and returns the access token. */
	public String login(ApiClient client, String email) {
		ResponseEntity<String> response = client.postJson("/api/auth/login",
				"{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD));
		if (response.getStatusCode() != HttpStatus.OK) {
			throw new AssertionError("Login of " + email + " failed: " + response.getStatusCode() + " " + response.getBody());
		}
		return JsonPath.read(response.getBody(), "$.accessToken");
	}

	private static String displayName(String email) {
		String local = email.substring(0, email.indexOf('@'));
		return Character.toUpperCase(local.charAt(0)) + local.substring(1);
	}

}

package com.burstintofire.bandcore.shared.web;

import org.springframework.http.HttpStatusCode;

/**
 * Stable, machine-readable error codes returned in the {@code code} property of every
 * Problem Details response. Clients (and translations in the web app) rely on these values,
 * so never rename one: add a new code instead.
 */
public enum ErrorCode {

	BAD_REQUEST,
	VALIDATION_FAILED,
	MALFORMED_REQUEST,
	UNAUTHENTICATED,
	ACCESS_DENIED,
	NOT_FOUND,
	METHOD_NOT_ALLOWED,
	NOT_ACCEPTABLE,
	CONFLICT,
	PAYLOAD_TOO_LARGE,
	UNSUPPORTED_MEDIA_TYPE,
	TOO_MANY_REQUESTS,
	INTERNAL_ERROR,
	SERVICE_UNAVAILABLE,

	// Bands
	SLUG_INVALID,
	SLUG_RESERVED,
	SLUG_TAKEN,
	TIMEZONE_INVALID,

	// Accounts
	PASSWORD_TOO_SHORT,
	PASSWORD_TOO_LONG,
	PASSWORD_TOO_COMMON,
	/** Verification (and later reset, invitation) token unknown, used or expired: one code for all. */
	TOKEN_INVALID,
	/** Wrong email or password, unverified email or no access to the band: one code for all. */
	INVALID_CREDENTIALS,
	/** The action needs a verified email (creating a band, accepting an invitation). */
	EMAIL_NOT_VERIFIED,
	BAND_LIMIT_REACHED,

	// Band workspace
	/** The member appears in expenses, splits or sales: deactivate instead of deleting. */
	MEMBER_HAS_HISTORY,
	/** A member can only be linked to an account with access to the same band. */
	USER_NOT_IN_BAND,
	/** The account is already linked to another member of the band. */
	ACCOUNT_ALREADY_LINKED,
	/** The member is already linked to an account. */
	MEMBER_ALREADY_LINKED,
	/** The email already has access to the band. */
	ALREADY_A_MEMBER,
	/** There is a pending invitation for this email: send it again instead. */
	INVITATION_PENDING,
	/** The invitation was sent to another email than the logged-in account's. */
	INVITATION_EMAIL_MISMATCH,
	/** The change would leave the band without an active admin. */
	LAST_ADMIN,

	// Finance
	/** Another category of the band already has this name (archived ones included). */
	CATEGORY_NAME_TAKEN,
	/** The category is used by expenses: archive it instead of deleting. */
	CATEGORY_IN_USE,
	/** Archived categories cannot be chosen for new expenses or moved to. */
	CATEGORY_ARCHIVED,
	/** A paid expense needs the member who paid it. */
	PAYER_REQUIRED,
	/** Only active members can pay or share new expenses. */
	MEMBER_INACTIVE,
	/** The action only applies to paid expenses (for example, changing who paid). */
	EXPENSE_NOT_PAID,
	/** The action only applies to planned expenses (for example, marking as paid). */
	EXPENSE_NOT_PLANNED,
	/** Someone else changed the record since it was read: reload and try again. */
	VERSION_CONFLICT,
	/** Custom amounts do not add up to the expense amount; {@code differenceCents} says by how much. */
	SPLIT_SUM_MISMATCH,
	/** Percentages do not add up to 100% (10000 basis points); see {@code differenceBasisPoints}. */
	SPLIT_PERCENT_SUM,
	/** The split has no members, repeats a member or misses a value its mode needs. */
	SPLIT_INVALID,
	/** A settlement goes from one member to another, never to the same member. */
	SETTLEMENT_SAME_MEMBER,

	// Merch
	/** Another product of the band already has this name (archived ones included). */
	PRODUCT_NAME_TAKEN,
	/** The product already has a variant with this size and color. */
	VARIANT_TAKEN,
	/** Another variant of the band already has this SKU. */
	SKU_TAKEN,
	/** The product has stock history: archive it instead of deleting. */
	PRODUCT_HAS_HISTORY,
	/** The variant has stock history: archive it instead of deleting. */
	VARIANT_HAS_HISTORY,
	/** A product keeps at least one variant: delete the product instead. */
	LAST_VARIANT,
	/** The product's currency cannot change once its variants have stock history. */
	PRODUCT_CURRENCY_LOCKED,
	/** The change would take a variant's stock below zero; {@code available} says what there is. */
	INSUFFICIENT_STOCK;

	/** Default code for a status when no more specific code applies. */
	public static ErrorCode forStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> BAD_REQUEST;
			case 401 -> UNAUTHENTICATED;
			case 403 -> ACCESS_DENIED;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 406 -> NOT_ACCEPTABLE;
			case 409 -> CONFLICT;
			case 413 -> PAYLOAD_TOO_LARGE;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			case 429 -> TOO_MANY_REQUESTS;
			case 503 -> SERVICE_UNAVAILABLE;
			default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
		};
	}

}

package com.burstintofire.bandcore.merch.stock;

/** Why an admin adjusted stock by hand; the database CHECK constraint lists the same values. */
public enum AdjustmentReason {

	/** A physical count found a different quantity. */
	COUNT,

	DAMAGED,

	LOST,

	/** Given away, for example to a fan or the press. */
	GIFT,

	/** Anything else; the note says what. */
	OTHER

}

package com.burstintofire.bandcore.merch.stock;

/** Why stock moved; the database CHECK constraint lists the same values. */
public enum MovementType {

	/** A production batch was received. */
	BATCH_IN,

	/** A production batch was cancelled, which takes its units back out. */
	BATCH_REVERSAL,

	SALE,

	/** A refunded item went back into stock. */
	REFUND,

	/** An admin corrected the stock by hand, with a reason. */
	ADJUSTMENT,

	/** An offline sale that exceeded the stock was resolved. */
	RECONCILIATION

}

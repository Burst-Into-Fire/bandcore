package com.burstintofire.bandcore.merch.batch;

/** The database CHECK constraint lists the same values. */
public enum BatchStatus {

	RECEIVED,

	/** Its units were taken back out of stock and the averages restored. */
	CANCELLED

}

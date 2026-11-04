package com.burstintofire.bandcore.merch.batch;

/** How a production batch's cost is given; the database CHECK constraint lists the same values. */
public enum CostMode {

	/** A unit cost per item; the batch total is their sum. */
	PER_ITEM,

	/** One total for the batch, allocated to the items by quantity. */
	ALLOCATED

}

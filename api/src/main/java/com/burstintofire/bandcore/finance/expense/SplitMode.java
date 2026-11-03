package com.burstintofire.bandcore.finance.expense;

/** How an expense is split between members. */
public enum SplitMode {

	/** Equal parts among the chosen members (by default, every active member). */
	EQUAL,

	/** An amount in cents per member, adding up exactly to the expense amount. */
	CUSTOM_AMOUNTS,

	/** A percentage per member in basis points, adding up to 10000 (100%). */
	CUSTOM_PERCENTAGES;

}

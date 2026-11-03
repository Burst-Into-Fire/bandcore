package com.burstintofire.bandcore.finance.expense;

/** Only paid expenses affect balances; planned ones are tracked until someone pays them. */
public enum ExpenseStatus {

	PLANNED,

	PAID;

}

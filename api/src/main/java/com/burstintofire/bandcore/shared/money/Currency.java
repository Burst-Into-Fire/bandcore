package com.burstintofire.bandcore.shared.money;

/**
 * The currencies bandcore handles (RNF08), as ISO 4217 codes. Both have two decimal places, so
 * amounts are always integer cents. Amounts in different currencies are never added together
 * (RN08).
 */
public enum Currency {

	BRL,

	USD;

}

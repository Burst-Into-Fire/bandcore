package com.burstintofire.bandcore.finance.category;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.tenancy.BandCreated;

/**
 * Gives every new band the default expense categories. Synchronous on purpose: it runs inside the
 * band creation transaction, so a band never exists without its categories.
 */
@Component
class DefaultCategoriesListener {

	private final ExpenseCategories categories;

	DefaultCategoriesListener(ExpenseCategories categories) {
		this.categories = categories;
	}

	@EventListener
	void on(BandCreated event) {
		categories.createDefaults(event.bandId());
	}

}

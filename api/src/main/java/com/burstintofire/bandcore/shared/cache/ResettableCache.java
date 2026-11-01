package com.burstintofire.bandcore.shared.cache;

/** A bean holding a cache that integration tests empty together with the database. */
public interface ResettableCache {

	void clear();

}

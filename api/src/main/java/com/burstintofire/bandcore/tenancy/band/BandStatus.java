package com.burstintofire.bandcore.tenancy.band;

public enum BandStatus {

	ACTIVE,
	/** Every request to the band host answers a generic 403. */
	SUSPENDED

}

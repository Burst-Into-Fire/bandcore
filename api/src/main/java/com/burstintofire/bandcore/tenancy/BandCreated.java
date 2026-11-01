package com.burstintofire.bandcore.tenancy;

import java.util.UUID;

/**
 * Published in the transaction that creates a band, after its first admin and member exist.
 * Listeners that add band data (for example default expense categories, M5-02) run in the same
 * transaction, with the new band's RLS context already set.
 */
public record BandCreated(UUID bandId, String slug, UUID createdBy) {

}

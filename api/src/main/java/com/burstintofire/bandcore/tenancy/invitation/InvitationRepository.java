package com.burstintofire.bandcore.tenancy.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query names the band (defense in depth on top of RLS). */
interface InvitationRepository extends TenantRepository<Invitation, UUID> {

	/** Locks the row, so accepting and revoking the same invitation are serialized. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Invitation> findByBandIdAndId(UUID bandId, UUID id);

	List<Invitation> findByBandIdOrderByCreatedAtDesc(UUID bandId);

	Optional<Invitation> findByBandIdAndEmailAndStatus(UUID bandId, String email, InvitationStatus status);

}

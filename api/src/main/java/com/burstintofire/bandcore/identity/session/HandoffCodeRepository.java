package com.burstintofire.bandcore.identity.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface HandoffCodeRepository extends JpaRepository<HandoffCode, UUID> {

	/** Locks the row, so two exchanges of the same code are serialized (and the second fails). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<HandoffCode> findByCodeHash(byte[] codeHash);

	/** Codes live for seconds; old rows are dropped whenever a new code is issued. */
	@Modifying
	@Query("DELETE FROM HandoffCode c WHERE c.expiresAt < :before")
	int deleteExpiredBefore(Instant before);

}

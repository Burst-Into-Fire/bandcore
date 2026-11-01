package com.burstintofire.bandcore.identity.reset;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	/** Locks the row, so two resets with the same token are serialized (and the second fails). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PasswordResetToken> findByTokenHash(byte[] tokenHash);

	/** A new request (or a completed reset) invalidates every other pending token. */
	@Modifying
	@Query("DELETE FROM PasswordResetToken t WHERE t.userId = :userId AND t.usedAt IS NULL")
	void deletePendingFor(UUID userId);

}

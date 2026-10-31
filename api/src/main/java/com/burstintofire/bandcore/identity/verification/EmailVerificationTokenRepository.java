package com.burstintofire.bandcore.identity.verification;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerificationToken> findByTokenHash(byte[] tokenHash);

	/** A new token (or a successful verification) invalidates every other pending token. */
	@Modifying
	@Query("DELETE FROM EmailVerificationToken t WHERE t.userId = :userId AND t.usedAt IS NULL")
	void deletePendingFor(UUID userId);

}

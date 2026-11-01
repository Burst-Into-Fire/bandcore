package com.burstintofire.bandcore.identity.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/** Locks the row, so two refreshes with the same token are serialized (and the second is reuse). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

	@Modifying
	@Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
	int revokeFamily(UUID familyId, Instant now);

	@Modifying
	@Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
	int revokeAllOfUser(UUID userId, Instant now);

	@Modifying
	@Query("""
			UPDATE RefreshToken t SET t.revokedAt = :now
			WHERE t.userId = :userId AND t.bandId = :bandId AND t.revokedAt IS NULL
			""")
	int revokeAllOfUserInBand(UUID userId, UUID bandId, Instant now);

}

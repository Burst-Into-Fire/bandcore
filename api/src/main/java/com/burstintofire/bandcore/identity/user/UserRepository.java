package com.burstintofire.bandcore.identity.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Global table: users have no band. Only the identity module may use it. */
public interface UserRepository extends JpaRepository<User, UUID> {

	/** @param email already normalized with {@link Emails#normalize} */
	Optional<User> findByEmail(String email);

}

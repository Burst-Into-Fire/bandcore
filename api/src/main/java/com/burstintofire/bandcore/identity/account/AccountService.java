package com.burstintofire.bandcore.identity.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.Accounts;
import com.burstintofire.bandcore.identity.user.User;
import com.burstintofire.bandcore.identity.user.UserRepository;

@Service
class AccountService implements Accounts {

	private final UserRepository users;

	AccountService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Account> find(UUID userId) {
		return users.findById(userId).map(AccountService::toAccount);
	}

	static Account toAccount(User user) {
		return new Account(user.getId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified(), user.isActive());
	}

}

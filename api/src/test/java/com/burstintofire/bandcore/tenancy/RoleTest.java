package com.burstintofire.bandcore.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.burstintofire.bandcore.shared.security.Permissions;

class RoleTest {

	@Test
	void adminHasEveryPermission() {
		assertThat(Role.ADMIN.permissions()).containsExactlyInAnyOrderElementsOf(Permissions.ALL);
	}

	@Test
	void readOnlyCanOnlyRead() {
		assertThat(Role.READ_ONLY.permissions()).allMatch(permission -> permission.endsWith(":read"))
			.doesNotContain(Permissions.AUDIT_READ);
	}

	@Test
	void readOnlySeesEverythingAMemberSees() {
		var memberReads = Role.MEMBER.permissions().stream().filter(permission -> permission.endsWith(":read")).toList();

		assertThat(Role.READ_ONLY.permissions()).containsExactlyInAnyOrderElementsOf(memberReads);
	}

	@Test
	void membersCannotManage() {
		assertThat(Role.MEMBER.permissions()).noneMatch(permission -> permission.endsWith(":manage"))
			.doesNotContain(Permissions.EVENT_CLOSE, Permissions.AUDIT_READ)
			.contains(Permissions.FINANCE_WRITE, Permissions.SALE_WRITE, Permissions.SALE_REFUND);
	}

	@Test
	void everyPermissionIsGrantedToSomeRole() {
		assertThat(Permissions.ALL).allMatch(permission -> Role.ADMIN.permissions().contains(permission));
	}

}

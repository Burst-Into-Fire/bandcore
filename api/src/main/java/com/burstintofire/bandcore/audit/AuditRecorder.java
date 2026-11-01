package com.burstintofire.bandcore.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records what happened to a band's data. Call it inside the transaction that makes the change:
 * the record is written in that transaction, so it is rolled back with a failed change and
 * committed with a successful one. The band is the one of the transaction's row-level security
 * context, the actor is the authenticated user, and the request id, client address and user
 * agent come from the current request.
 */
public interface AuditRecorder {

	/**
	 * @param action {@code entity.verb} in lowercase, for example {@code expense.created}
	 * @param entityType lowercase, for example {@code expense}
	 * @param entityId the changed entity, if it has an id
	 * @param changes what changed, for example {@code {"name": {"from": "A", "to": "B"}}}; never
	 *        passwords, tokens or personal data that the trail does not need
	 * @throws org.springframework.transaction.IllegalTransactionStateException outside a transaction
	 */
	void record(String action, String entityType, UUID entityId, Map<String, ?> changes);

	/** A {@code changes} entry for a field that changed from one value to another. */
	static Map<String, Object> change(Object from, Object to) {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("from", from);
		change.put("to", to);
		return change;
	}

}

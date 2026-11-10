package com.burstintofire.bandcore.merch.sale;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.persistence.AssignedIdEntity;

/** Tenant table: an immutable completed sale and the client's idempotency key. */
@Entity
@Table(name = "sales")
public class Sale extends AssignedIdEntity {

	@Column(name = "band_id", nullable = false, updatable = false)
	private UUID bandId;

	@Column(name = "event_id", updatable = false)
	private UUID eventId;

	@Column(name = "seller_user_id", nullable = false, updatable = false)
	private UUID sellerUserId;

	@Column(name = "sold_at", nullable = false, updatable = false)
	private Instant soldAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, updatable = false)
	private PaymentMethod paymentMethod;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Currency currency;

	@Column(name = "total_cents", nullable = false, updatable = false)
	private long totalCents;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private SaleStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private SaleSource source;

	@Column(name = "request_hash", nullable = false, updatable = false)
	private String requestHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Sale() {
	}

	Sale(UUID id, UUID bandId, UUID eventId, UUID sellerUserId, Instant soldAt, PaymentMethod paymentMethod,
			Currency currency, long totalCents, String requestHash, Instant createdAt) {
		super(id);
		this.bandId = bandId;
		this.eventId = eventId;
		this.sellerUserId = sellerUserId;
		this.soldAt = soldAt;
		this.paymentMethod = paymentMethod;
		this.currency = currency;
		this.totalCents = totalCents;
		this.status = SaleStatus.COMPLETED;
		this.source = SaleSource.ONLINE;
		this.requestHash = requestHash;
		this.createdAt = createdAt;
	}

	public UUID getBandId() {
		return bandId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getSellerUserId() {
		return sellerUserId;
	}

	public Instant getSoldAt() {
		return soldAt;
	}

	public PaymentMethod getPaymentMethod() {
		return paymentMethod;
	}

	public Currency getCurrency() {
		return currency;
	}

	public long getTotalCents() {
		return totalCents;
	}

	public SaleStatus getStatus() {
		return status;
	}

	public SaleSource getSource() {
		return source;
	}

	String getRequestHash() {
		return requestHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

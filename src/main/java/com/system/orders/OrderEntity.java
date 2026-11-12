package com.system.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@Column(nullable = false)
	private long totalCents;

	@Column(nullable = false, length = 3)
	private String currencyCode;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<OrderItemEntity> items = new ArrayList<>();

	protected OrderEntity() {
	}

	public OrderEntity(long userId, String currencyCode, Instant createdAt) {
		this.userId = userId;
		this.currencyCode = currencyCode;
		this.createdAt = createdAt;
		this.status = OrderStatus.PENDING;
	}

	public void addItem(long productId, String productName, int quantity, long unitPriceCents) {
		var item = new OrderItemEntity(this, productId, productName, quantity, unitPriceCents);
		items.add(item);
		totalCents = Math.addExact(totalCents, Math.multiplyExact(quantity, unitPriceCents));
	}

	public Long getId() { return id; }
	public long getUserId() { return userId; }
	public OrderStatus getStatus() { return status; }
	public long getTotalCents() { return totalCents; }
	public String getCurrencyCode() { return currencyCode; }
	public Instant getCreatedAt() { return createdAt; }
	public List<OrderItemEntity> getItems() { return items; }
	public void setStatus(OrderStatus status) { this.status = status; }
}

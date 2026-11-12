package com.system.orders;

import com.system.orders.grpc.OrdersServiceV1.CreateOrderRequest;
import com.system.orders.grpc.OrdersServiceV1.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrderService {

	private final OrderRepository orderRepository;

	public OrderService(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@Transactional
	public OrderEntity create(CreateOrderRequest request) {
		validateCreateRequest(request);
		var order = new OrderEntity(request.getUserId(), request.getCurrencyCode().trim().toUpperCase(), Instant.now());
		request.getItemsList().forEach(item -> order.addItem(
				item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPriceCents()));
		return orderRepository.save(order);
	}

	@Transactional(readOnly = true)
	public OrderEntity get(long id) {
		return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public List<OrderEntity> list(long userId) {
		return userId == 0 ? orderRepository.findAll() : orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public OrderEntity updateStatus(long id, OrderStatus status) {
		if (status == null) {
			throw new InvalidOrderException("status is required");
		}
		var order = get(id);
		order.setStatus(status);
		return order;
	}

	@Transactional
	public OrderEntity cancel(long id) {
		return updateStatus(id, OrderStatus.CANCELLED);
	}

	private void validateCreateRequest(CreateOrderRequest request) {
		if (request.getUserId() <= 0) throw new InvalidOrderException("user_id must be greater than zero");
		if (request.getItemsCount() == 0) throw new InvalidOrderException("at least one item is required");
		if (!request.getCurrencyCode().matches("[A-Za-z]{3}")) throw new InvalidOrderException("currency_code must be a three-letter ISO code");
		for (OrderItem item : request.getItemsList()) {
			if (item.getProductId() <= 0 || item.getProductName().isBlank() || item.getQuantity() <= 0 || item.getUnitPriceCents() < 0) {
				throw new InvalidOrderException("each item requires a positive product_id and quantity, a name, and a non-negative unit_price_cents");
			}
		}
	}
}

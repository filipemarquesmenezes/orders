package com.system.orders.grpc;

import com.google.protobuf.util.Timestamps;
import com.system.orders.InvalidOrderException;
import com.system.orders.OrderEntity;
import com.system.orders.OrderItemEntity;
import com.system.orders.OrderNotFoundException;
import com.system.orders.OrderService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class OrdersGrpcService extends OrdersGrpc.OrdersImplBase {

	private final OrderService orderService;

	public OrdersGrpcService(OrderService orderService) {
		this.orderService = orderService;
	}

	@Override
	public void createOrder(OrdersServiceV1.CreateOrderRequest request, StreamObserver<OrdersServiceV1.Order> observer) {
		execute(observer, () -> toProto(orderService.create(request)));
	}

	@Override
	public void getOrder(OrdersServiceV1.GetOrderRequest request, StreamObserver<OrdersServiceV1.Order> observer) {
		execute(observer, () -> toProto(orderService.get(request.getId())));
	}

	@Override
	public void listOrders(OrdersServiceV1.ListOrdersRequest request, StreamObserver<OrdersServiceV1.OrderListResponse> observer) {
		execute(observer, () -> OrdersServiceV1.OrderListResponse.newBuilder()
				.addAllOrders(orderService.list(request.getUserId()).stream().map(this::toProto).toList())
				.build());
	}

	@Override
	public void updateOrderStatus(OrdersServiceV1.UpdateOrderStatusRequest request, StreamObserver<OrdersServiceV1.Order> observer) {
		execute(observer, () -> toProto(orderService.updateStatus(request.getId(), toDomainStatus(request.getStatus()))));
	}

	@Override
	public void cancelOrder(OrdersServiceV1.CancelOrderRequest request, StreamObserver<OrdersServiceV1.Order> observer) {
		execute(observer, () -> toProto(orderService.cancel(request.getId())));
	}

	private <T> void execute(StreamObserver<T> observer, java.util.function.Supplier<T> operation) {
		try {
			observer.onNext(operation.get());
			observer.onCompleted();
		} catch (InvalidOrderException exception) {
			observer.onError(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException());
		} catch (OrderNotFoundException exception) {
			observer.onError(Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
		} catch (ArithmeticException exception) {
			observer.onError(Status.INVALID_ARGUMENT.withDescription("order total is too large").asRuntimeException());
		} catch (RuntimeException exception) {
			observer.onError(Status.INTERNAL.withDescription("unable to process order").asRuntimeException());
		}
	}

	private OrdersServiceV1.Order toProto(OrderEntity order) {
		return OrdersServiceV1.Order.newBuilder()
				.setId(order.getId())
				.setUserId(order.getUserId())
				.setStatus(OrdersServiceV1.OrderStatus.valueOf("ORDER_STATUS_" + order.getStatus().name()))
				.addAllItems(order.getItems().stream().map(this::toProto).toList())
				.setTotalCents(order.getTotalCents())
				.setCurrencyCode(order.getCurrencyCode())
				.setCreatedAt(Timestamps.fromMillis(order.getCreatedAt().toEpochMilli()))
				.build();
	}

	private OrdersServiceV1.OrderItem toProto(OrderItemEntity item) {
		return OrdersServiceV1.OrderItem.newBuilder()
				.setProductId(item.getProductId())
				.setProductName(item.getProductName())
				.setQuantity(item.getQuantity())
				.setUnitPriceCents(item.getUnitPriceCents())
				.build();
	}

	private com.system.orders.OrderStatus toDomainStatus(OrdersServiceV1.OrderStatus status) {
		if (status == OrdersServiceV1.OrderStatus.ORDER_STATUS_UNSPECIFIED || status == OrdersServiceV1.OrderStatus.UNRECOGNIZED) {
			throw new InvalidOrderException("a concrete order status is required");
		}
		return com.system.orders.OrderStatus.valueOf(status.name().replace("ORDER_STATUS_", ""));
	}
}

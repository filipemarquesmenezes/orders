package com.system;

import com.system.orders.grpc.OrdersGrpc;
import com.system.orders.grpc.OrdersServiceV1;
import com.system.orders.OrderRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.grpc.server.port=0")
class OrdersApplicationTests {

	@LocalGrpcServerPort
	private Integer grpcPort;
	@Autowired
	private OrderRepository orderRepository;
	private ManagedChannel channel;
	private OrdersGrpc.OrdersBlockingStub blockingStub;

	@BeforeEach
	void setUp() {
		orderRepository.deleteAll();
		channel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
		blockingStub = OrdersGrpc.newBlockingStub(channel);
	}

	@AfterEach
	void tearDown() {
		channel.shutdownNow();
	}

	@Test
	void createsUpdatesListsAndCancelsAnOrder() {
		var created = blockingStub.createOrder(createRequest(42));
		assertEquals(42, created.getUserId());
		assertEquals(650, created.getTotalCents());
		assertEquals("USD", created.getCurrencyCode());
		assertEquals(1, created.getItemsCount());
		assertEquals(OrdersServiceV1.OrderStatus.ORDER_STATUS_PENDING, created.getStatus());
		assertEquals(created, blockingStub.getOrder(OrdersServiceV1.GetOrderRequest.newBuilder().setId(created.getId()).build()));

		var updated = blockingStub.updateOrderStatus(OrdersServiceV1.UpdateOrderStatusRequest.newBuilder()
				.setId(created.getId()).setStatus(OrdersServiceV1.OrderStatus.ORDER_STATUS_CONFIRMED).build());
		assertEquals(OrdersServiceV1.OrderStatus.ORDER_STATUS_CONFIRMED, updated.getStatus());

		blockingStub.createOrder(createRequest(99));
		assertEquals(1, blockingStub.listOrders(OrdersServiceV1.ListOrdersRequest.newBuilder().setUserId(42).build()).getOrdersCount());
		assertEquals(2, blockingStub.listOrders(OrdersServiceV1.ListOrdersRequest.getDefaultInstance()).getOrdersCount());
		assertEquals(OrdersServiceV1.OrderStatus.ORDER_STATUS_CANCELLED, blockingStub.cancelOrder(
				OrdersServiceV1.CancelOrderRequest.newBuilder().setId(created.getId()).build()).getStatus());
	}

	@Test
	void rejectsInvalidRequests() {
		var exception = assertThrows(StatusRuntimeException.class, () -> blockingStub.createOrder(
				OrdersServiceV1.CreateOrderRequest.newBuilder().setUserId(42).setCurrencyCode("USD").build()));
		assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());

		var created = blockingStub.createOrder(createRequest(42));
		var invalidStatus = assertThrows(StatusRuntimeException.class, () -> blockingStub.updateOrderStatus(
				OrdersServiceV1.UpdateOrderStatusRequest.newBuilder().setId(created.getId()).build()));
		assertEquals(Status.Code.INVALID_ARGUMENT, invalidStatus.getStatus().getCode());
	}

	@Test
	void returnsNotFoundForAnUnknownOrder() {
		var exception = assertThrows(StatusRuntimeException.class, () -> blockingStub.getOrder(
				OrdersServiceV1.GetOrderRequest.newBuilder().setId(999_999).build()));
		assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
	}

	private OrdersServiceV1.CreateOrderRequest createRequest(long userId) {
		return OrdersServiceV1.CreateOrderRequest.newBuilder()
				.setUserId(userId)
				.setCurrencyCode("usd")
				.addItems(OrdersServiceV1.OrderItem.newBuilder().setProductId(7).setProductName("Notebook").setQuantity(2).setUnitPriceCents(325))
				.build();
	}
}

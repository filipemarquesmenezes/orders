package com.system.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
	List<OrderEntity> findAllByUserIdOrderByCreatedAtDesc(long userId);
}

package org.alim.observability.order.service;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import org.alim.observability.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}

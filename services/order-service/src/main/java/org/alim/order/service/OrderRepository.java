package org.alim.order.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.alim.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Ownership is part of the lookup so another user's order is indistinguishable from a missing one.
    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}

package org.alim.order.api;

import org.alim.order.domain.Order;
import org.alim.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(

  UUID id,

  UUID customerId,

  BigDecimal amount,

  OrderStatus status,

  Instant createdAt,

  Instant updatedAt
) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
      order.getId(),
      order.getCustomerId(),
      order.getAmount(),
      order.getStatus(),
            order.getCreatedAt(),
            order.getUpdatedAt()
    );
  }
}

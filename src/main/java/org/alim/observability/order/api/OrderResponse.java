package org.alim.observability.order.api;

import org.alim.observability.order.domain.Order;
import org.alim.observability.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(

  UUID id,

  UUID customerId,

  BigDecimal amount,

  OrderStatus status,

  Instant createdAt
) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
      order.getId(),
      order.getCustomerId(),
      order.getAmount(),
      order.getStatus(),
      order.getCreatedAt()
    );
  }
}

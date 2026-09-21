package org.alim.observability.order.api;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(

  @NotNull(message = "customerId is required")
  UUID customerId,

  @NotNull(message = "amount is required")
  @Positive(message = "amount must be greater than zero")
  BigDecimal amount
) {
}

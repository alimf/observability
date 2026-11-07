package org.alim.order.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The customer is the authenticated user (token subject), never a body field.
 */
public record CreateOrderRequest(

  @NotNull(message = "amount is required")
  @Positive(message = "amount must be greater than zero")
  BigDecimal amount
) {
}

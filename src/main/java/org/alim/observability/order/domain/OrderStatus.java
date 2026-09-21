package org.alim.observability.order.domain;

public enum OrderStatus {
  CREATED,
  PAYMENT_PENDING,
  PAID,
  FAILED,
  CANCELLED
}

package org.alim.order.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentClient {

  void authorize(UUID orderId, BigDecimal amount);
}

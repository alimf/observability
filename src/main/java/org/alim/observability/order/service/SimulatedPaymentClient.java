package org.alim.observability.order.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Stand-in for a real payment gateway integration. Simulates network latency
 * and a realistic failure rate so the metrics/alerts/dashboards in this
 * project have something meaningful to react to.
 */
@Component
@Slf4j
public class SimulatedPaymentClient implements PaymentClient {

  private static final double FAILURE_RATE = 0.1;

  @Override
  @Retry(name = "paymentClient")
  @CircuitBreaker(name = "paymentClient")
  public void authorize(UUID orderId, BigDecimal amount) {

    simulateNetworkLatency();

    if (ThreadLocalRandom.current().nextDouble() < FAILURE_RATE) {
      log.warn("Payment authorization declined for order {}", orderId);
      throw new PaymentException("Payment gateway declined authorization for order " + orderId);
    }
  }

  private void simulateNetworkLatency() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(20, 150));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PaymentException("Payment authorization interrupted", e);
    }
  }
}

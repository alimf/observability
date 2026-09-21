package org.alim.observability.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class OrderMetrics {

  private final Counter ordersCreated;

  private final Counter orderCreationFailures;

  private final Counter paymentSuccess;

  private final Timer orderCreationDuration;

  public OrderMetrics(MeterRegistry registry) {

    ordersCreated = Counter.builder(
        "orders.created"
      )
      .description("Number of orders created")
      .register(registry);

    orderCreationFailures = Counter.builder(
        "orders.creation.failures"
      )
      .description("Number of order creation failures")
      .register(registry);

    paymentSuccess = Counter.builder(
        "orders.payment.success"
      )
      .description("Number of successful payments")
      .register(registry);

    orderCreationDuration = Timer.builder(
        "orders.creation.duration"
      )
      .description("Order creation duration")
      .publishPercentileHistogram()
      .register(registry);
  }

  public void ordersCreated() {
    ordersCreated.increment();
  }

  public void orderCreationFailure() {
    orderCreationFailures.increment();
  }

  public void orderPaymentSuccess() {
    paymentSuccess.increment();
  }

  public Timer orderCreationDuration() {
    return orderCreationDuration;
  }
}

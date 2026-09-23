package org.alim.observability.order.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import org.alim.observability.observability.OrderMetrics;
import org.alim.observability.order.api.CreateOrderRequest;
import org.alim.observability.order.api.OrderResponse;
import org.alim.observability.order.domain.Order;
import org.alim.observability.order.domain.OrderNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private final OrderRepository orderRepository;
  private final PaymentClient paymentClient;
  private final OrderMetrics metrics;

  @Transactional
  public OrderResponse create(CreateOrderRequest request) {

    Order order = createPendingOrder(request);

    Timer.Sample timer = Timer.start();

    try {

      paymentClient.authorize(order.getId(), order.getAmount());

      metrics.orderPaymentSuccess();

      return OrderResponse.from(markPaid(order.getId()));

    } catch (Exception exception) {

      metrics.orderCreationFailure();
      markFailed(order.getId());

      throw exception;

    } finally {

      timer.stop(metrics.orderCreationDuration());
    }
  }

  @Transactional
  Order createPendingOrder(CreateOrderRequest request) {

    Order order = Order.create(
      request.customerId(),
      request.amount()
    );

    orderRepository.save(order);

    metrics.ordersCreated();

    return order;
  }

  @Transactional
  Order markPaid(UUID orderId) {

    Order order = orderRepository
      .findById(orderId)
      .orElseThrow(() -> new OrderNotFoundException(orderId));

    order.markPaid();

    return order;
  }

  @Transactional
  void markFailed(UUID orderId) {

    orderRepository
      .findById(orderId)
      .ifPresent(Order::markFailed);
  }

  public OrderResponse get(UUID id) {

    return orderRepository
      .findById(id)
      .map(OrderResponse::from)
      .orElseThrow(
        () -> new OrderNotFoundException(id)
      );
  }
}

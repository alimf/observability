package org.alim.observability.order.service;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

  private final OrderRepository orderRepository;
  private final PaymentClient paymentClient;
  private final OrderMetrics metrics;

  public OrderResponse create(CreateOrderRequest request) {

    Timer.Sample timer =
      Timer.start();

    try {

      Order order = Order.create(
        request.customerId(),
        request.amount()
      );

      orderRepository.save(order);

      metrics.ordersCreated();

      paymentClient.authorize(
        order.getId(),
        order.getAmount()
      );

      order.markPaid();

      metrics.orderPaymentSuccess();

      return OrderResponse.from(order);

    } catch (Exception exception) {

      metrics.orderCreationFailure();

      throw exception;

    } finally {

      timer.stop(
        metrics.orderCreationDuration()
      );
    }
  }

  @Transactional(readOnly = true)
  public OrderResponse get(UUID id) {

    return orderRepository
      .findById(id)
      .map(OrderResponse::from)
      .orElseThrow(
        () -> new OrderNotFoundException(id)
      );
  }
}

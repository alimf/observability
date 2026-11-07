package org.alim.order.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.alim.order.observability.OrderMetrics;
import org.alim.order.api.CreateOrderRequest;
import org.alim.order.api.OrderResponse;
import org.alim.order.api.PageResponse;
import org.alim.order.api.UpdateOrderRequest;
import org.alim.order.domain.Order;
import org.alim.order.domain.OrderNotFoundException;
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
  public OrderResponse create(UUID userId, CreateOrderRequest request) {

      Order order = createPendingOrder(userId, request);

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
  Order createPendingOrder(UUID userId, CreateOrderRequest request) {

    Order order = Order.create(
            userId,
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

    public OrderResponse get(UUID userId, UUID id) {
        return OrderResponse.from(findOwned(userId, id));
    }

    public PageResponse<OrderResponse> list(UUID userId, int page, int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))
        );

        return PageResponse.from(
                orderRepository.findByCustomerId(userId, pageable),
                OrderResponse::from
        );
    }

    @Transactional
    public OrderResponse update(UUID userId, UUID id, UpdateOrderRequest request) {

        Order order = findOwned(userId, id);

        switch (request.status()) {
            case CANCELLED -> order.cancel();
        }

        return OrderResponse.from(order);
    }

    private Order findOwned(UUID userId, UUID id) {

    return orderRepository
            .findByIdAndCustomerId(id, userId)
            .orElseThrow(() -> new OrderNotFoundException(id));
  }
}

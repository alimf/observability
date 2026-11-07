package org.alim.order.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.alim.order.observability.OrderMetrics;
import org.alim.order.api.CreateOrderRequest;
import org.alim.order.api.OrderResponse;
import org.alim.order.api.UpdateOrderRequest;
import org.alim.order.domain.OrderNotFoundException;
import org.alim.order.domain.OrderStateException;
import org.alim.order.domain.Order;
import org.alim.order.domain.OrderStatus;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private PaymentClient paymentClient;

  @Mock
  private OrderMetrics metrics;

  private OrderService orderService;

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final BigDecimal AMOUNT = BigDecimal.valueOf(42);

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orderRepository, paymentClient, metrics);
      lenient().when(orderRepository.save(any(Order.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));
      lenient().when(metrics.orderCreationDuration())
            .thenReturn(Timer.builder("orders.creation.duration").register(new SimpleMeterRegistry()));
  }

  @Test
  void createReturnsPaidOrderWhenPaymentSucceeds() {

    Order order = Order.create(CUSTOMER_ID, AMOUNT);
    when(orderRepository.findById(any())).thenReturn(Optional.of(order));

      OrderResponse response = orderService.create(CUSTOMER_ID, new CreateOrderRequest(AMOUNT));

    assertThat(response.status()).isEqualTo(OrderStatus.PAID);
    verify(metrics).ordersCreated();
    verify(metrics).orderPaymentSuccess();
  }

  @Test
  void createMarksOrderFailedAndPropagatesWhenPaymentFails() {

    Order order = Order.create(CUSTOMER_ID, AMOUNT);
    when(orderRepository.findById(any())).thenReturn(Optional.of(order));
    doThrow(new PaymentException("declined")).when(paymentClient).authorize(any(), any());

      assertThatThrownBy(() -> orderService.create(CUSTOMER_ID, new CreateOrderRequest(AMOUNT)))
      .isInstanceOf(PaymentException.class);

    ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(savedOrder.capture());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    verify(metrics).orderCreationFailure();
  }

    @Test
    void createAssignsOrderToAuthenticatedUser() {

        Order order = Order.create(CUSTOMER_ID, AMOUNT);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        orderService.create(CUSTOMER_ID, new CreateOrderRequest(AMOUNT));

        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedOrder.capture());
        assertThat(savedOrder.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void getHidesOrdersOwnedByOtherUsers() {

        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerId(orderId, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get(CUSTOMER_ID, orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateCancelsOwnedOrder() {

        UUID orderId = UUID.randomUUID();
        Order order = Order.create(CUSTOMER_ID, AMOUNT);
        order.markPaid();
        when(orderRepository.findByIdAndCustomerId(orderId, CUSTOMER_ID)).thenReturn(Optional.of(order));

        OrderResponse response =
                orderService.update(CUSTOMER_ID, orderId, new UpdateOrderRequest(UpdateOrderRequest.Status.CANCELLED));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateRejectsCancellingAnAlreadyCancelledOrder() {

        UUID orderId = UUID.randomUUID();
        Order order = Order.create(CUSTOMER_ID, AMOUNT);
        order.cancel();
        when(orderRepository.findByIdAndCustomerId(orderId, CUSTOMER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                orderService.update(CUSTOMER_ID, orderId, new UpdateOrderRequest(UpdateOrderRequest.Status.CANCELLED)))
                .isInstanceOf(OrderStateException.class);
    }
}

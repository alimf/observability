package org.alim.observability.order.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.alim.observability.observability.OrderMetrics;
import org.alim.observability.order.api.CreateOrderRequest;
import org.alim.observability.order.api.OrderResponse;
import org.alim.observability.order.domain.Order;
import org.alim.observability.order.domain.OrderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    when(orderRepository.save(any(Order.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createReturnsPaidOrderWhenPaymentSucceeds() {

    Order order = Order.create(CUSTOMER_ID, AMOUNT);
    when(orderRepository.findById(any())).thenReturn(Optional.of(order));

    OrderResponse response = orderService.create(new CreateOrderRequest(CUSTOMER_ID, AMOUNT));

    assertThat(response.status()).isEqualTo(OrderStatus.PAID);
    verify(metrics).ordersCreated();
    verify(metrics).orderPaymentSuccess();
  }

  @Test
  void createMarksOrderFailedAndPropagatesWhenPaymentFails() {

    Order order = Order.create(CUSTOMER_ID, AMOUNT);
    when(orderRepository.findById(any())).thenReturn(Optional.of(order));
    doThrow(new PaymentException("declined")).when(paymentClient).authorize(any(), any());

    assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(CUSTOMER_ID, AMOUNT)))
      .isInstanceOf(PaymentException.class);

    ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(savedOrder.capture());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    verify(metrics).orderCreationFailure();
  }
}

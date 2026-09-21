package org.alim.observability.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
  name = "orders",
  indexes = {
    @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
    @Index(name = "idx_orders_status", columnList = "status")
  }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  private Order(
    UUID customerId,
    BigDecimal amount
  ) {
    this.customerId = customerId;
    this.amount = amount;
    this.status = OrderStatus.CREATED;
    this.createdAt = Instant.now();
  }

  public static Order create(
    UUID customerId,
    BigDecimal amount
  ) {
    return new Order(customerId, amount);
  }

  public void markPaid() {
    this.status = OrderStatus.PAID;
  }

  public void markFailed() {
    this.status = OrderStatus.FAILED;
  }
}

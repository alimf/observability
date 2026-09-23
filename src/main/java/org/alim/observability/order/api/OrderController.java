package org.alim.observability.order.api;

import org.alim.observability.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> create(
    @Valid @RequestBody CreateOrderRequest request
  ) {

    OrderResponse response =
      orderService.create(request);

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(response);
  }

  @GetMapping("/{id}")
  public OrderResponse get(
    @PathVariable UUID id
  ) {
    return orderService.get(id);
  }
}

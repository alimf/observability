package org.alim.order.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.alim.common.security.CurrentUser;
import org.alim.order.service.OrderService;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Every operation acts on behalf of the authenticated user and only ever sees that user's orders.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> create(
          @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody CreateOrderRequest request
  ) {

    OrderResponse response =
            orderService.create(CurrentUser.id(jwt), request);

    return ResponseEntity
            .created(
                    ServletUriComponentsBuilder
                            .fromCurrentRequest()
                            .path("/{id}")
                            .buildAndExpand(response.id())
                            .toUri()
            )
      .body(response);
  }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return orderService.list(CurrentUser.id(jwt), page, size);
  }

  @GetMapping("/{id}")
  public OrderResponse get(
          @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID id
  ) {
      return orderService.get(CurrentUser.id(jwt), id);
  }

    @PatchMapping("/{id}")
    public OrderResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderRequest request
    ) {
        return orderService.update(CurrentUser.id(jwt), id, request);
  }
}

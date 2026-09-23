package org.alim.observability.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.alim.observability.order.domain.OrderNotFoundException;
import org.alim.observability.order.service.PaymentException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(
    OrderNotFoundException exception
  ) {

    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ApiError.of(
          "ORDER_NOT_FOUND",
          exception.getMessage()
        )
      );
  }

  @ExceptionHandler(PaymentException.class)
  public ResponseEntity<ApiError> handlePaymentFailure(
    PaymentException exception
  ) {

    log.warn("Payment failure: {}", exception.getMessage());

    return ResponseEntity
      .status(HttpStatus.BAD_GATEWAY)
      .body(
        ApiError.of(
          "PAYMENT_FAILED",
          exception.getMessage()
        )
      );
  }

  @ExceptionHandler(CallNotPermittedException.class)
  public ResponseEntity<ApiError> handleCircuitOpen(
    CallNotPermittedException exception
  ) {

    log.warn("Payment circuit breaker open: {}", exception.getMessage());

    return ResponseEntity
      .status(HttpStatus.SERVICE_UNAVAILABLE)
      .body(
        ApiError.of(
          "PAYMENT_SERVICE_UNAVAILABLE",
          "Payment service is temporarily unavailable, please retry shortly"
        )
      );
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
    MethodArgumentNotValidException exception
  ) {

    return ResponseEntity
      .badRequest()
      .body(
        ApiError.of(
          "VALIDATION_ERROR",
          "Request validation failed"
        )
      );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(
    Exception exception
  ) {

    log.error(
      "Unexpected application error",
      exception
    );

    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ApiError.of(
          "INTERNAL_ERROR",
          "An unexpected error occurred"
        )
      );
  }
}

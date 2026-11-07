package org.alim.order.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.alim.common.web.ProblemDetails;
import org.alim.order.domain.OrderNotFoundException;
import org.alim.order.domain.OrderStateException;
import org.alim.order.service.PaymentException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain errors; framework and catch-all errors come from the common module's advice.
 */
@RestControllerAdvice
@Order(0)
@Slf4j
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(OrderNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ProblemDetails.of(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(OrderStateException.class)
    public ResponseEntity<ProblemDetail> handleState(OrderStateException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ProblemDetails.of(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentUpdate(ObjectOptimisticLockingFailureException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ProblemDetails.of(
                        HttpStatus.CONFLICT,
                        "CONCURRENT_MODIFICATION",
                        "The order was modified by another request, please retry"
                ));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ProblemDetail> handlePaymentFailure(PaymentException exception) {

        log.warn("Payment failure: {}", exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ProblemDetails.of(HttpStatus.BAD_GATEWAY, "PAYMENT_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitOpen(CallNotPermittedException exception) {

        log.warn("Payment circuit breaker open: {}", exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ProblemDetails.of(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PAYMENT_SERVICE_UNAVAILABLE",
                        "Payment service is temporarily unavailable, please retry shortly"
                ));
    }
}

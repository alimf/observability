package org.alim.observability.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

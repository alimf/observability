package org.alim.user.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.alim.common.web.ProblemDetails;
import org.alim.user.domain.EmailAlreadyRegisteredException;
import org.alim.user.domain.InvalidCredentialsException;
import org.alim.user.domain.UserNotFoundException;

/**
 * Domain errors; framework and catch-all errors come from the common module's advice.
 */
@RestControllerAdvice
@Order(0)
public class UserExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleEmailTaken(EmailAlreadyRegisteredException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ProblemDetails.of(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException exception) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(UserNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ProblemDetails.of(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage()));
    }
}

package com.swiftship.exception;

import com.swiftship.dto.common.ErrorDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ShipmentNotFoundException.class)
  public ResponseEntity<ErrorDetails> handleNotFound(ShipmentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(buildError(404, "Not Found", ex.getMessage()));
  }

  @ExceptionHandler(CarrierNotFoundException.class)
  public ResponseEntity<ErrorDetails> handleNotFound(CarrierNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(buildError(404, "Not Found", ex.getMessage()));
  }

  @ExceptionHandler(InvalidStatusTransitionException.class)
  public ResponseEntity<ErrorDetails> handleInvalidTransition(InvalidStatusTransitionException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(buildError(422, "Invalid Status Transition", ex.getMessage()));
  }

  @ExceptionHandler(CarrierCapacityExceededException.class)
  public ResponseEntity<ErrorDetails> handleCapacity(CarrierCapacityExceededException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(buildError(409, "Carrier Capacity Exceeded", ex.getMessage()));
  }

  @ExceptionHandler(ReturnWindowExpiredException.class)
  public ResponseEntity<ErrorDetails> handleReturnWindow(ReturnWindowExpiredException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(buildError(422, "Return Window Expired", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDetails> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getFieldErrors()
        .stream()
        .map(e -> e.getField() + ": " + e.getDefaultMessage())
        .toList();

    ErrorDetails error = ErrorDetails.builder()
        .status(400)
        .error("Validation Failed")
        .message("Request body has validation errors")
        .details(details)
        .timestamp(Instant.now())
        .build();

    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorDetails> handleGeneral(Exception ex) {
    log.error("Unhandled exception — this should be investigated", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(buildError(500, "Internal Server Error",
            "An unexpected error occurred. Please try again later."));
  }

  private ErrorDetails buildError(int status, String error, String message) {
    return ErrorDetails.builder()
        .status(status)
        .error(error)
        .message(message)
        .timestamp(Instant.now())
        .build();
  }
}

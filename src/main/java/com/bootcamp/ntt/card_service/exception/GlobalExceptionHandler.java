package com.bootcamp.ntt.card_service.exception;

import com.bootcamp.ntt.card_service.model.ErrorResponse;

import java.time.OffsetDateTime;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CardServiceException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleCardServiceException(CardServiceException ex) {
    log.warn("Card service exception: {} - {}", ex.getErrorCode(), ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode(ex.getErrorCode());
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(errorResponse));
  }

  @ExceptionHandler(BusinessRuleException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleBusinessRuleException(BusinessRuleException ex) {
    log.warn("Business rule violation: {} - {}", ex.getCode(), ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode(ex.getCode());
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
  }

  @ExceptionHandler(CustomerNotFoundException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleCustomerNotFoundException(CustomerNotFoundException ex) {
    log.warn("Customer not found: {}", ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("CUSTOMER_NOT_FOUND");
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
  }

  @ExceptionHandler(CustomerServiceException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleCustomerServiceException(CustomerServiceException ex) {
    log.error("Customer service error: {}", ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("CUSTOMER_SERVICE_ERROR");
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
  }

  @ExceptionHandler(WebClientResponseException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleWebClientResponseException(WebClientResponseException ex) {
    log.error("WebClient error - Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());

    ErrorResponse errorResponse = new ErrorResponse();

    if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
      errorResponse.setCode("CUSTOMER_NOT_FOUND");
      errorResponse.setMessage("Customer with provided ID does not exist");
      return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
    } else if (ex.getStatusCode().is5xxServerError()) {
      errorResponse.setCode("CUSTOMER_SERVICE_ERROR");
      errorResponse.setMessage("Customer service is temporarily unavailable");
      return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
    } else {
      errorResponse.setCode("CUSTOMER_SERVICE_ERROR");
      errorResponse.setMessage("Error communicating with customer service");
      return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse));
    }
  }

  @ExceptionHandler(CustomerServiceUnavailableException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleCustomerServiceUnavailable(CustomerServiceUnavailableException ex) {
    log.error("Customer service unavailable - card creation blocked: {}", ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("CUSTOMER_SERVICE_UNAVAILABLE");
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
  }

  @ExceptionHandler(TransactionServiceUnavailableException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleTransactionServiceUnavailable(TransactionServiceUnavailableException ex) {
    log.error("Transaction service unavailable: {}", ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("TRANSACTION_SERVICE_UNAVAILABLE");
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleValidationException(WebExchangeBindException ex) {
    log.warn("Validation error: {}", ex.getMessage());

    String message = ex.getBindingResult().getFieldErrors().stream()
      .map(error -> error.getField() + ": " + error.getDefaultMessage())
      .reduce((error1, error2) -> error1 + ", " + error2)
      .orElse("Validation error");

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("VALIDATION_ERROR");
    errorResponse.setMessage(message);
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleAccessDeniedException(AccessDeniedException ex, ServerWebExchange exchange) {
    log.warn("Access denied: {} - Path: {}", ex.getMessage(), exchange.getRequest().getPath().value());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("ACCESS_DENIED");
    errorResponse.setMessage(ex.getMessage());
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
  }

  @ExceptionHandler(RuntimeException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleRuntimeException(RuntimeException ex) {
    log.error("Runtime exception: {}", ex.getMessage(), ex);

    if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode("RESOURCE_NOT_FOUND");
      errorResponse.setMessage(ex.getMessage());
      errorResponse.setTimestamp(OffsetDateTime.now());
      return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
    }

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("INTERNAL_SERVER_ERROR");
    errorResponse.setMessage("An unexpected error occurred");
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
  }

  // 2. Handlers MÁS GENERALES al final (ESTE DEBE SER EL ÚLTIMO)
  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
    log.error("Unexpected exception: {}", ex.getMessage(), ex);

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("INTERNAL_SERVER_ERROR");
    errorResponse.setMessage("An unexpected error occurred");
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
  }

  /*@ExceptionHandler(ServerWebInputException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleServerWebInputException(ServerWebInputException ex) {
    log.warn("Invalid request payload: {}", ex.getMessage());

    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode("INVALID_AMOUNT_FORMAT");
    errorResponse.setMessage("The amount must be a valid number");
    errorResponse.setTimestamp(OffsetDateTime.now());

    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
  }*/
}

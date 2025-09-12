package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerServiceClient;
import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerResponse;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.exception.CustomerServiceUnavailableException;
import com.bootcamp.ntt.card_service.service.ExternalServiceWrapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalServiceWrapperImpl implements ExternalServiceWrapper {

  private final CustomerServiceClient customerServiceClient;
  private final TransactionServiceClient transactionServiceClient;
  private final CircuitBreaker customerServiceCircuitBreaker;
  private final CircuitBreaker transactionServiceCircuitBreaker;
  private final TimeLimiter customerServiceTimeLimiter;
  private final TimeLimiter transactionServiceTimeLimiter;

  /**
   * Llama al customer-service con circuit breaker y timeout de 2s
   */
  public Mono<CustomerTypeResponse> getCustomerTypeWithCircuitBreaker(String customerId) {
    return customerServiceClient.getCustomerType(customerId)
      .transformDeferred(CircuitBreakerOperator.of(customerServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(customerServiceTimeLimiter))
      .doOnError(error -> log.warn("Customer service call failed for customerId={}: {}",
        customerId, error.getMessage()))
      .onErrorResume(this::handleCustomerServiceError);
  }

  /**
   * Llama al customer-service para obtener customer completo
   */
  public Mono<CustomerResponse> getCustomerWithCircuitBreaker(String customerId) {
    return customerServiceClient.getCustomer(customerId)
      .transformDeferred(CircuitBreakerOperator.of(customerServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(customerServiceTimeLimiter))
      .doOnError(error -> log.warn("Customer service call failed for customerId={}: {}",
        customerId, error.getMessage()))
      .onErrorResume(error -> handleCustomerNotFoundError(customerId, error));
  }

  /**
   * Llama al transaction-service con circuit breaker
   */
  public Mono<Void> createTransactionWithCircuitBreaker(TransactionRequest transactionRequest) {
    return transactionServiceClient.createTransaction(transactionRequest)
      .transformDeferred(CircuitBreakerOperator.of(transactionServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(transactionServiceTimeLimiter))
      .doOnError(error -> log.error("Transaction service call failed for cardId={}: {}",
        transactionRequest.getCardId(), error.getMessage()))
      .onErrorResume(this::handleTransactionServiceError);
  }

  // Fallback methods
  private Mono<CustomerTypeResponse> handleCustomerServiceError(Throwable error) {
    log.error("Customer service unavailable - blocking card creation for security: {}", error.getMessage());

    return Mono.error(new CustomerServiceUnavailableException(
      "Customer validation service temporarily unavailable. Card creation blocked for security. Please try again later."));
  }

  private Mono<CustomerResponse> handleCustomerNotFoundError(String customerId, Throwable error) {
    log.error("Customer service unavailable for customerId={}: {}", customerId, error.getMessage());
    return Mono.error(new RuntimeException("Customer service temporarily unavailable. Please try again later."));
  }

  private Mono<Void> handleTransactionServiceError(Throwable error) {
    // Para transacciones, solo log del error - la transacción principal continúa
    log.error("Transaction service unavailable - transaction not recorded: {}", error.getMessage());
    return Mono.empty(); // Continúa sin fallar la operación principal
  }
}

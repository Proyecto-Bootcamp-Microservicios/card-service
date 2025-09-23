package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.AccountServiceClient;
import com.bootcamp.ntt.card_service.client.CustomerServiceClient;
import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.account.*;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerResponse;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionResponse;
import com.bootcamp.ntt.card_service.exception.AccountServiceUnavailableException;
import com.bootcamp.ntt.card_service.exception.CustomerServiceUnavailableException;
import com.bootcamp.ntt.card_service.exception.TransactionServiceUnavailableException;
import com.bootcamp.ntt.card_service.model.TransactionsSummary;
import com.bootcamp.ntt.card_service.service.ExternalServiceWrapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalServiceWrapperImpl implements ExternalServiceWrapper {

  private final CustomerServiceClient customerServiceClient;
  private final TransactionServiceClient transactionServiceClient;
  private final AccountServiceClient accountServiceClient;

  private final CircuitBreaker customerServiceCircuitBreaker;
  private final CircuitBreaker transactionServiceCircuitBreaker;
  private final CircuitBreaker accountServiceCircuitBreaker;

  private final TimeLimiter customerServiceTimeLimiter;
  private final TimeLimiter transactionServiceTimeLimiter;
  private final TimeLimiter accountServiceTimeLimiter;

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

  /**
   * Llama al account-service para obtener balance con circuit breaker
   */
  @Override
  public Mono<AccountBalanceResponse> getAccountBalanceWithCircuitBreaker(String accountId) {
    return accountServiceClient.getAccountBalance(accountId)
      .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter))
      .doOnError(error -> log.warn("Account service call failed for accountId={}: {}",
        accountId, error.getMessage()))
      .onErrorResume(error -> handleAccountBalanceError(accountId, error));
  }

  /**
   * Llama al account-service para débito con circuit breaker
   */
  @Override
  public Mono<AccountTransactionResponse> debitAccountWithCircuitBreaker(String accountId, AccountDebitRequest request) {
    return accountServiceClient.debitAccount(accountId, request)
      .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter))
      .doOnError(error -> log.warn("Account debit service call failed for accountId={}: {}",
        accountId, error.getMessage()))
      .onErrorResume(error -> handleAccountDebitError(accountId, error));
  }

  /**
   * Llama al account-service para obtener detalles con circuit breaker
   */
  @Override
  public Mono<AccountDetailsResponse> getAccountDetailsWithCircuitBreaker(String accountId) {
    return accountServiceClient.getAccountDetails(accountId)
      .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter))
      .doOnError(error -> log.warn("Account details service call failed for accountId={}: {}",
        accountId, error.getMessage()))
      .onErrorResume(error -> handleAccountDetailsError(accountId, error));
  }

  @Override
  public Mono<AccountTransactionResponse> creditAccountWithCircuitBreaker(String accountId, AccountCreditRequest request) {
    return accountServiceClient.creditAccount(accountId, request)
      .transformDeferred(CircuitBreakerOperator.of(accountServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(accountServiceTimeLimiter))
      .doOnError(error -> log.warn("Account credit service call failed for accountId={}: {}",
        accountId, error.getMessage()))
      .onErrorResume(error -> handleAccountCreditError(accountId, error));
  }

  /**
   * Llama al transaction-service para obtener resumen de transacciones con circuit breaker
   */
  @Override
  public Mono<TransactionsSummary> getDebitCardTransactionsSummaryWithCircuitBreaker(LocalDate startDate, LocalDate endDate) {
    return transactionServiceClient.getDebitCardTransactionsSummary(startDate, endDate)
      .transformDeferred(CircuitBreakerOperator.of(transactionServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(transactionServiceTimeLimiter))
      .doOnError(error -> log.warn("Transaction summary service call failed for period {}-{}: {}",
        startDate, endDate, error.getMessage()))
      .onErrorResume(error -> handleTransactionSummaryError(startDate, endDate, error));
  }

  /**
   * Llama al transaction-service para obtener movimientos con circuit breaker
   */
  @Override
  public Flux<TransactionResponse> getLastCardMovementsWithCircuitBreaker(String cardId, Integer limit) {
    return transactionServiceClient.getLastCardMovements(cardId, limit)
      .transformDeferred(CircuitBreakerOperator.of(transactionServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(transactionServiceTimeLimiter))
      .doOnError(error -> log.warn("Card movements service call failed for cardId={}: {}",
        cardId, error.getMessage()))
      .onErrorResume(error -> handleCardMovementsError(cardId, error));
  }

  /**
   * Llama al transaction-service específico para débito con circuit breaker
   */
  @Override
  public Mono<Void> createDebitCardPurchaseTransactionWithCircuitBreaker(TransactionRequest transactionRequest) {
    return transactionServiceClient.createDebitCardPurchaseTransaction(transactionRequest)
      .transformDeferred(CircuitBreakerOperator.of(transactionServiceCircuitBreaker))
      .transformDeferred(TimeLimiterOperator.of(transactionServiceTimeLimiter))
      .doOnError(error -> log.error("Debit purchase transaction service call failed for cardNumber={}: {}",
        transactionRequest.getCardNumber(), error.getMessage()))
      .onErrorResume(this::handleDebitPurchaseTransactionServiceError);
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
    return Mono.error(new TransactionServiceUnavailableException(
      "Transaction service temporarily unavailable. Charge authorization blocked for data consistency."
    ));
  }

  private Mono<AccountBalanceResponse> handleAccountBalanceError(String accountId, Throwable error) {
    log.error("Account balance service unavailable for accountId={}: {}", accountId, error.getMessage());
    return Mono.error(new AccountServiceUnavailableException(
      "Account balance service temporarily unavailable. Please try again later."));
  }

  private Mono<AccountTransactionResponse> handleAccountDebitError(String accountId, Throwable error) {
    log.error("Account debit service unavailable for accountId={}: {}", accountId, error.getMessage());
    return Mono.error(new AccountServiceUnavailableException(
      "Account debit service temporarily unavailable. Transaction cannot be processed."));
  }

  private Mono<AccountDetailsResponse> handleAccountDetailsError(String accountId, Throwable error) {
    log.error("Account details service unavailable for accountId={}: {}", accountId, error.getMessage());
    return Mono.error(new AccountServiceUnavailableException(
      "Account details service temporarily unavailable. Please try again later."));
  }

  private Mono<AccountTransactionResponse> handleAccountCreditError(String accountId, Throwable error) {
    log.error("Account credit service unavailable for accountId={}: {}", accountId, error.getMessage());
    return Mono.error(new AccountServiceUnavailableException(
      "Account credit service temporarily unavailable. Cannot revert transaction."));
  }

  private Mono<TransactionsSummary> handleTransactionSummaryError(LocalDate startDate, LocalDate endDate, Throwable error) {
    log.warn("Transaction summary service unavailable for period {}-{}, returning empty summary",
      startDate, endDate);

    TransactionsSummary emptySummary = new TransactionsSummary();
    emptySummary.setTotalTransactions(0);
    emptySummary.setTotalAmount(0.0);
    return Mono.just(emptySummary);
  }

  private Flux<TransactionResponse> handleCardMovementsError(String cardId, Throwable error) {
    log.warn("Card movements service unavailable for cardId={}, returning empty movements", cardId);
    return Flux.empty();
  }

  private Mono<Void> handleDebitPurchaseTransactionServiceError(Throwable error) {
    return Mono.error(new TransactionServiceUnavailableException(
      "Debit purchase transaction service temporarily unavailable. Purchase cannot be recorded."
    ));
  }
}

package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.client.dto.account.*;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerResponse;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionResponse;
import com.bootcamp.ntt.card_service.model.TransactionsSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ExternalServiceWrapper {

  Mono<CustomerTypeResponse> getCustomerTypeWithCircuitBreaker(String customerId);

  Mono<CustomerResponse> getCustomerWithCircuitBreaker(String customerId);

  Mono<Void> createTransactionWithCircuitBreaker(TransactionRequest transactionRequest);

  Mono<AccountBalanceResponse> getAccountBalanceWithCircuitBreaker(String accountId);

  Mono<AccountTransactionResponse> debitAccountWithCircuitBreaker(String accountId, AccountDebitRequest request);

  Mono<AccountDetailsResponse> getAccountDetailsWithCircuitBreaker(String accountId);

  Mono<AccountTransactionResponse> creditAccountWithCircuitBreaker(String accountId, AccountCreditRequest request);

  Mono<TransactionsSummary> getDebitCardTransactionsSummaryWithCircuitBreaker(LocalDate startDate, LocalDate endDate);

  Flux<TransactionResponse> getLastCardMovementsWithCircuitBreaker(String cardId, Integer limit);
}

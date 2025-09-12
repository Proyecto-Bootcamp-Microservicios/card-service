package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.client.dto.customer.CustomerResponse;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import reactor.core.publisher.Mono;

public interface ExternalServiceWrapper {

  Mono<CustomerTypeResponse> getCustomerTypeWithCircuitBreaker(String customerId);

  Mono<CustomerResponse> getCustomerWithCircuitBreaker(String customerId);

  Mono<Void> createTransactionWithCircuitBreaker(TransactionRequest transactionRequest);

}

package com.bootcamp.ntt.card_service.client;

import com.bootcamp.ntt.card_service.client.dto.customer.CustomerResponse;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.card_service.exception.CustomerNotFoundException;
import com.bootcamp.ntt.card_service.exception.CustomerServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceClient {

  private final WebClient webClient;

  @Value("${services.customer.base-url:http://localhost:8080}")
  private String customerServiceUrl;

  public Mono<CustomerResponse> getCustomer(String customerId) {
    log.debug("Fetching customer details for ID: {}", customerId);

    return webClient
      .get()
      .uri(customerServiceUrl + "/customers/{id}", customerId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("Customer not found: {}", customerId);
        return Mono.error(new CustomerNotFoundException("Customer not found: " + customerId));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Customer service error for customer: {}", customerId);
        return Mono.error(new CustomerServiceException("Error communicating with customer service"));
      })
      .bodyToMono(CustomerResponse.class)
      .doOnSuccess(response -> log.debug("Customer details retrieved for ID: {}", customerId))
      .doOnError(error -> log.error("Error fetching customer details: {}", error.getMessage()));
  }

  public Mono<CustomerTypeResponse> getCustomerType(String customerId) {
    log.debug("Fetching customer type for ID: {}", customerId);

    return webClient
      .get()
      .uri(customerServiceUrl + "/customers/{id}", customerId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError,
        response -> {
          log.warn("Customer not found: {}", customerId);
          return Mono.error(new CustomerNotFoundException("Customer not found: " + customerId));
        })
      .onStatus(HttpStatus::is5xxServerError,
        response -> {
          log.error("Customer service error for customer: {}", customerId);
          return Mono.error(new CustomerServiceException("Error communicating with customer service"));
        })
      .bodyToMono(CustomerTypeResponse.class)
      .doOnSuccess(response -> log.debug("Customer type retrieved: {} for ID: {}",
        response.getCustomerType(), customerId))
      .doOnError(error -> log.error("Error fetching customer type: {}", error.getMessage()));
  }

  public Mono<Boolean> customerExists(String customerId) {
    log.debug("Checking if customer exists: {}", customerId);

    return webClient
      .get()
      .uri(customerServiceUrl + "/customers/{id}/exists", customerId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.error("Customer not found with id: {}", customerId);
        return Mono.error(new CustomerNotFoundException("Error searching the customer"));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Customer service error checking existence for: {}", customerId);
        return Mono.error(new CustomerServiceException("Error checking customer existence"));
      })
      .bodyToMono(Boolean.class)
      .defaultIfEmpty(false)
      .doOnSuccess(exists -> log.debug("Customer existence check result for {}: {}", customerId, exists))
      .doOnError(error -> log.error("Error checking customer existence: {}", error.getMessage()));
  }
}

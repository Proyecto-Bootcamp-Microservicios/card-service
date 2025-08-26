package com.bootcamp.ntt.card_service.client;

import com.bootcamp.ntt.card_service.client.dto.CustomerTypeResponse;
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
public class CustomerClient {

  private final WebClient webClient;

  @Value("${services.customer.base-url}")
  private String customerServiceUrl;

  public Mono<CustomerTypeResponse> getCustomerType(String customerId) {
    log.debug("Fetching customer type for ID: {}", customerId);

    return webClient
      .get()
      .uri(customerServiceUrl + "/customers/{id}/type", customerId)
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
        response.getType(), customerId))
      .doOnError(error -> log.error("Error fetching customer type: {}", error.getMessage()));
  }

}

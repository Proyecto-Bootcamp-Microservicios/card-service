package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.CustomersApiDelegate;
import com.bootcamp.ntt.card_service.model.CustomerCardsSummaryResponse;
import com.bootcamp.ntt.card_service.model.ProductEligibilityResponse;
import com.bootcamp.ntt.card_service.service.CardConsolidationService;
import com.bootcamp.ntt.card_service.service.CreditCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomersApiDelegateImpl implements CustomersApiDelegate {

  private final CardConsolidationService cardConsolidationService;
  private final CreditCardService creditCardService;

  /**
   * GET /customers/{customerId}/cards-summary : Get customer cards summary
   */
  @Override
  public Mono<ResponseEntity<CustomerCardsSummaryResponse>> getCustomerCardsSummary(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Getting cards summary for customer ID: {}", customerId);

    return cardConsolidationService
      .getCustomerCardsSummary(customerId)
      .map(response -> {
        log.info("Cards summary retrieved successfully for customer: {} with {} total cards",
          customerId, response.getTotalActiveCards());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * GET /customers/{customerId}/product-eligibility : Check customer eligibility
   */
  @Override
  public Mono<ResponseEntity<ProductEligibilityResponse>> checkCustomerProductEligibility(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Checking product eligibility for customer ID: {}", customerId);

    return creditCardService // o el servicio que manejes para esto
      .checkCustomerProductEligibility(customerId)
      .map(response -> {
        log.info("Eligibility checked for customer: {} - Eligible: {}",
          customerId, response.getIsEligible());
        return ResponseEntity.ok(response);
      });
  }

}

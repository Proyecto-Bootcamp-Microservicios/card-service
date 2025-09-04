package com.bootcamp.ntt.card_service.service;
import com.bootcamp.ntt.card_service.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface DebitCardService {
  Mono<DebitPurchaseResponse> processDebitCardPurchase(String cardNumber, DebitPurchaseRequest request);

  Mono<PrimaryAccountBalanceResponse> getDebitCardPrimaryAccountBalance(String cardId);

  Mono<CardMovementsResponse> getCardMovements(String cardId, Integer limit);

  Mono<ProductEligibilityResponse> checkCustomerProductEligibility(String customerId);

  Mono<CustomerCardsSummaryResponse> getCustomerCardsSummary(String customerId);
}

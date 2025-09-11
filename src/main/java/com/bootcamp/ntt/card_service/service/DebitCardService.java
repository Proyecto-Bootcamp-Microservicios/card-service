package com.bootcamp.ntt.card_service.service;
import com.bootcamp.ntt.card_service.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface DebitCardService {
  //Flux<DebitCardResponse> getAllDebitCards(Boolean isActive);

  Flux<DebitCardResponse> getDebitCardsByActive(Boolean isActive);

  Mono<DebitCardResponse> getCardById(String id);

  Flux<DebitCardResponse> getDebitCardsByActiveAndCustomer(Boolean isActive, String customerId);

  Mono<DebitCardResponse> createCard(DebitCardCreateRequest cardRequest);

  Mono<DebitCardResponse> updateCard(String id, DebitCardUpdateRequest cardRequest);

  Mono<Void> deleteCard(String id);

  Mono<DebitCardResponse> deactivateCard(String id);

  Mono<DebitCardResponse> activateCard(String id);

  Mono<DebitCardResponse> associateAccountToDebitCard(String debitCardId, AssociateAccountRequest request);

  Mono<DebitPurchaseResponse> processDebitCardPurchase(String cardNumber, DebitPurchaseRequest request);

  Mono<PrimaryAccountBalanceResponse> getDebitCardPrimaryAccountBalance(String cardId);

  Mono<Integer> getActiveCardsCount();

  //Mono<CustomerCardsSummaryResponse> getCustomerCardsSummary(String customerId);
}

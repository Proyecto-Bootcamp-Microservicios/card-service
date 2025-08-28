package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.model.CardCreateRequest;
import com.bootcamp.ntt.card_service.model.CardResponse;
import com.bootcamp.ntt.card_service.model.CardUpdateRequest;
import com.bootcamp.ntt.card_service.model.CreditReservationRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;


public interface CardService {

  Flux<CardResponse> getAllCards(Boolean isActive);

  Flux<CardResponse> getCardsByActive(Boolean isActive);

  Mono<CardResponse> getCardById(String id);

  Flux<CardResponse> getCardsByActiveAndCustomer(Boolean isActive, String customerId);


  Mono<CardResponse> createCard(CardCreateRequest cardRequest);

  Mono<CardResponse> updateCard(String id, CardUpdateRequest cardRequest);

  Mono<Void> deleteCard(String id);

  Mono<CardResponse> deactivateCard(String id);

  Mono<CardResponse> activateCard(String id);

  Mono<CardResponse> reserveCredit(String cardId, CreditReservationRequest request);

  //Flux<CardResponse> getActiveCards(Boolean isActive);
}

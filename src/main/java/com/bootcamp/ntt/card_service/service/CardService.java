package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.repository.CardRepository;
import com.bootcamp.ntt.card_service.entity.Card;
import com.bootcamp.ntt.cardservice.model.CardCreateRequest;
import com.bootcamp.ntt.cardservice.model.CardResponse;
import com.bootcamp.ntt.cardservice.model.CardUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public interface CardService {

  Flux<CardResponse> getAllCards(Boolean isActive);

  Mono<CardResponse> getCardById(String id);

  Mono<CardResponse> createCard(CardCreateRequest cardRequest);

  Mono<CardResponse> updateCard(String id, CardUpdateRequest cardRequest);

  Mono<Void> deleteCard(String id);

  Mono<CardResponse> deactivateCard(String id);

  Mono<CardResponse> activateCard(String id);

  //Flux<CardResponse> getActiveCards(Boolean isActive);
}

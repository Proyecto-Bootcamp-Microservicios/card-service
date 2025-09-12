package com.bootcamp.ntt.card_service.repository;

import com.bootcamp.ntt.card_service.entity.Card;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CardRepository extends ReactiveMongoRepository<Card, String> {

  Flux<Card> findByIsActive(Boolean isActive);
  Flux<Card> findByCustomerId(String customerId);
  Mono<Card> findByCardNumber(String cardNumber);
  Mono<Long> countByCustomerIdAndIsActiveTrue(String customerId);
  Flux<Card> findByIsActiveAndCustomerId(Boolean isActive, String customerId);
}

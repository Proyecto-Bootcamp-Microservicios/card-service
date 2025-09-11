package com.bootcamp.ntt.card_service.repository;

import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.enums.CreditCardType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CreditCardRepository extends ReactiveMongoRepository<CreditCard, String> {
  Flux<CreditCard> findByIsActiveAndType(Boolean isActive, CardType type);
  Flux<CreditCard> findByIsActiveAndCustomerIdAndType(Boolean isActive, String customerId, CardType type);
  Flux<CreditCard>findByCustomerId(String customerId);
  Mono<CreditCard> findByCardNumber(String cardNumber);
  Mono<Long> countByCustomerIdAndIsActiveTrue(String customerId);
  Mono<Long> countByIsActiveAndType(Boolean isActive, CardType type);
}

package com.bootcamp.ntt.card_service.repository;

import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.enums.CreditCardType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CreditCardRepository extends ReactiveMongoRepository<CreditCard, String> {
  Flux<CreditCard> findByIsActive(Boolean isActive);
  Flux<CreditCard> findByCustomerId(String customerId);
  Mono<CreditCard> findByCardNumber(String cardNumber);
  Mono<Long> countByCustomerIdAndIsActiveTrue(String customerId);
  Flux<CreditCard> findByIsActiveAndCustomerId(Boolean isActive, String customerId);
  Flux<CreditCard> findByCreditCardType(CreditCardType type);
}

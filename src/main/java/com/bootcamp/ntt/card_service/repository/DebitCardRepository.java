package com.bootcamp.ntt.card_service.repository;
import com.bootcamp.ntt.card_service.entity.DebitCard;
import com.bootcamp.ntt.card_service.enums.CardType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DebitCardRepository extends ReactiveMongoRepository<DebitCard, String> {
  Flux<DebitCard> findByIsActiveAndType(Boolean isActive, CardType type);
  Flux<DebitCard> findByIsActiveAndCustomerIdAndType(Boolean isActive, String customerId, CardType type);
  Flux<DebitCard> findByCustomerId(String customerId);
  Mono<DebitCard> findByCardNumber(String cardNumber);
  Mono<Long> countByIsActiveAndType(Boolean isActive, CardType type);

  //Flux<DebitCard> findByIsActiveAndCustomerId(Boolean isActive, String customerId);
  Flux<DebitCard> findByPrimaryAccountId(String accountId);
  Flux<DebitCard> findByAssociatedAccountIdsContaining(String accountId);
}

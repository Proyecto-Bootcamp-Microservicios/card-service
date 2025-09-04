package com.bootcamp.ntt.card_service.repository;
import com.bootcamp.ntt.card_service.entity.DebitCard;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DebitCardRepository extends ReactiveMongoRepository<DebitCard, String> {
  Flux<DebitCard> findByIsActive(Boolean isActive);
  Flux<DebitCard> findByCustomerId(String customerId);
  Mono<DebitCard> findByCardNumber(String cardNumber);
  Flux<DebitCard> findByPrimaryAccountId(String accountId);
  Flux<DebitCard> findByAssociatedAccountIdsContaining(String accountId);
}

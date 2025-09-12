package com.bootcamp.ntt.card_service.repository;

import com.bootcamp.ntt.card_service.entity.DailyBalance;

import java.time.LocalDate;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DailyBalanceRepository extends ReactiveMongoRepository<DailyBalance, String> {

  Flux<DailyBalance> findByCustomerIdAndDateBetween(String customerId, LocalDate startDate, LocalDate endDate);

  Mono<Boolean> existsByCardIdAndDate(String cardId, LocalDate date);
}

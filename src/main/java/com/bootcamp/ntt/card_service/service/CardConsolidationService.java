package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.model.CardsPeriodicReportResponse;
import com.bootcamp.ntt.card_service.model.CustomerCardsSummaryResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface CardConsolidationService {
  Mono<CustomerCardsSummaryResponse> getCustomerCardsSummary(String customerId);
  Mono<CardsPeriodicReportResponse> generateCardsPeriodicReport(LocalDate startDate, LocalDate endDate);

}

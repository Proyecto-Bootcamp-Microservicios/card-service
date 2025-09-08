package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.CardConsolidationService;
import com.bootcamp.ntt.card_service.service.CreditCardService;
import com.bootcamp.ntt.card_service.service.DebitCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardConsolidationServiceImpl implements CardConsolidationService {

  private final DebitCardService debitCardService;
  private final CreditCardService creditCardService;
  private final TransactionServiceClient transactionServiceClient;

  @Override
  public Mono<CustomerCardsSummaryResponse> getCustomerCardsSummary(String customerId) {
    log.debug("Building cards summary for customer: {}", customerId);

    return Mono.zip(
        debitCardService.getDebitCardsByActiveAndCustomer(true, customerId).collectList(),
        creditCardService.getCardsByActiveAndCustomer(true, customerId).collectList()
      )
      .map(tuple -> buildSummaryResponse(customerId, tuple.getT1(), tuple.getT2()))
      .doOnSuccess(response -> log.debug("Summary built for customer: {} with {} total cards",
        customerId, response.getTotalActiveCards()))
      .doOnError(error -> log.error("Error building summary for customer {}: {}", customerId, error.getMessage()));
  }

  private CustomerCardsSummaryResponse buildSummaryResponse(String customerId,
                                                            List<DebitCardResponse> debitCards,
                                                            List<CreditCardResponse> creditCards) {
    CustomerCardsSummaryResponse response = new CustomerCardsSummaryResponse();
    response.setCustomerId(customerId);
    response.setCreditCards(mapCreditCardsSummary(creditCards));
    response.setDebitCards(mapDebitCardsSummary(debitCards));
    response.setTotalActiveCards(creditCards.size() + debitCards.size());
    response.setRetrievedAt(OffsetDateTime.now());
    return response;
  }

  // Mapeos simplificados para MVP
  private List<CreditCardSummary> mapCreditCardsSummary(List<CreditCardResponse> creditCards) {
    return creditCards.stream()
      .map(card -> {
        CreditCardSummary summary = new CreditCardSummary();
        summary.setCardId(card.getId());
        summary.setCardNumber(card.getCardNumber()); // Enmascarar por seguridad
        summary.setAvailableCredit(card.getAvailableCredit() != null ? BigDecimal.valueOf(card.getAvailableCredit()) : BigDecimal.ZERO);
        summary.setCurrentBalance(card.getCurrentBalance() != null ? BigDecimal.valueOf(card.getCurrentBalance()) : BigDecimal.ZERO);
        summary.setIsActive(card.getIsActive());
        return summary;
      })
      .collect(Collectors.toList());
  }

  private List<DebitCardSummary> mapDebitCardsSummary(List<DebitCardResponse> debitCards) {
    return debitCards.stream()
      .map(card -> {
        DebitCardSummary summary = new DebitCardSummary();
        summary.setCardId(card.getId());
        summary.setCardNumber(card.getCardNumber());
        summary.setPrimaryAccountId(card.getPrimaryAccountId());
        summary.setIsActive(card.getIsActive());
        return summary;
      })
      .collect(Collectors.toList());
  }

  @Override
  public Mono<CardsPeriodicReportResponse> generateCardsPeriodicReport(LocalDate startDate, LocalDate endDate) {
    log.debug("Generating cards report for period: {} to {}", startDate, endDate);

    return Mono.zip(
        generateCreditCardsReport(startDate, endDate),
        generateDebitCardsReport(startDate, endDate)
      )
      .map(tuple -> buildPeriodicReportResponse(startDate, endDate, tuple.getT1(), tuple.getT2()))
      .doOnSuccess(response -> log.debug("Report generated for period: {} to {}", startDate, endDate));
  }

  private Mono<CreditCardsReport> generateCreditCardsReport(LocalDate startDate, LocalDate endDate) {
    return creditCardService.getActiveCardsCount()
      .zipWith(
        Mono.just(createEmptyTransactionsSummary()) // Mock para pruebas locales
      )
      .map(tuple -> {
        Integer activeCards = tuple.getT1();
        TransactionsSummary summary = tuple.getT2();

        CreditCardsReport report = new CreditCardsReport();
        report.setTotalActiveCards(activeCards);
        report.setTotalTransactions(summary.getTotalTransactions());
        report.setTotalAmountTransacted(summary.getTotalAmount());
        report.setAverageCreditUtilization(0.0); // Simplificado para MVP
        return report;
      });
  }

  private Mono<DebitCardsReport> generateDebitCardsReport(LocalDate startDate, LocalDate endDate) {
    return debitCardService.getActiveCardsCount()
      .zipWith(
        transactionServiceClient.getDebitCardTransactionsSummary(startDate, endDate)
          .onErrorReturn(createEmptyTransactionsSummary()) // Fallback para MVP
      )
      .map(tuple -> {
        Integer activeCards = tuple.getT1();
        TransactionsSummary summary = tuple.getT2();

        DebitCardsReport report = new DebitCardsReport();
        report.setTotalActiveCards(activeCards);
        report.setTotalTransactions(summary.getTotalTransactions());
        report.setTotalAmountTransacted(summary.getTotalAmount());
        return report;
      });
  }

  private CardsPeriodicReportResponse buildPeriodicReportResponse(LocalDate startDate, LocalDate endDate,
                                                                  CreditCardsReport creditReport,
                                                                  DebitCardsReport debitReport) {
    CardsPeriodicReportResponse response = new CardsPeriodicReportResponse();

    // Configurar período del reporte
    CardsPeriodicReportResponseReportPeriod period = new CardsPeriodicReportResponseReportPeriod();
    period.setStartDate(startDate);
    period.setEndDate(endDate);
    response.setReportPeriod(period);

    // Configurar reportes
    response.setCreditCards(creditReport);
    response.setDebitCards(debitReport);
    response.setGeneratedAt(OffsetDateTime.now());

    return response;
  }

  private TransactionsSummary createEmptyTransactionsSummary() {
    TransactionsSummary summary = new TransactionsSummary();
    summary.setTotalTransactions(0);
    summary.setTotalAmount(0.0);
    return summary;
  }
}

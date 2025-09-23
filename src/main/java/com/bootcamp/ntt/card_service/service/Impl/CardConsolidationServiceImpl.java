package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionAccount;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionResponse;
import com.bootcamp.ntt.card_service.exception.CardServiceException;
import com.bootcamp.ntt.card_service.mapper.CardMapper;
import com.bootcamp.ntt.card_service.mapper.CreditCardMapper;
import com.bootcamp.ntt.card_service.mapper.DebitCardMapper;
import com.bootcamp.ntt.card_service.model.CustomerCardsSummaryResponse;
import com.bootcamp.ntt.card_service.model.CardsPeriodicReportResponse;
import com.bootcamp.ntt.card_service.model.CardsPeriodicReportResponseReportPeriod;
import com.bootcamp.ntt.card_service.model.CardMovementsResponse;
import com.bootcamp.ntt.card_service.model.CreditCardResponse;
import com.bootcamp.ntt.card_service.model.CreditCardsReport;
import com.bootcamp.ntt.card_service.model.DebitCardResponse;
import com.bootcamp.ntt.card_service.model.DebitCardsReport;
import com.bootcamp.ntt.card_service.model.TransactionsSummary;
import com.bootcamp.ntt.card_service.service.CardConsolidationService;
import com.bootcamp.ntt.card_service.service.CreditCardService;
import com.bootcamp.ntt.card_service.service.DebitCardService;
import com.bootcamp.ntt.card_service.service.ExternalServiceWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardConsolidationServiceImpl implements CardConsolidationService {

  private final DebitCardService debitCardService;
  private final CreditCardService creditCardService;
  private final CreditCardMapper creditCardMapper;
  private final DebitCardMapper debitCardMapper;
  private final CardMapper cardMapper;
  private final TransactionServiceClient transactionServiceClient;
  private final ExternalServiceWrapper externalServiceWrapper;

  @Override
  public Mono<CustomerCardsSummaryResponse> getCustomerCardsSummary(String customerId) {
    log.debug("Building cards summary for customer: {}", customerId);

    return Mono.zip(
        debitCardService.getDebitCardsByActiveAndCustomer(true, customerId).collectList(),
        creditCardService.getCardsByActiveAndCustomer(true, customerId).collectList()
      )
      .map(tuple -> buildSummaryResponse(customerId, tuple.getT1(), tuple.getT2()))
      .doOnSuccess(response -> log.debug("Summary built for customer: {} with {} total cards",
        customerId, response.getTotalActiveCards()));
  }

  private CustomerCardsSummaryResponse buildSummaryResponse(String customerId,
                                                            List<DebitCardResponse> debitCards,
                                                            List<CreditCardResponse> creditCards) {
    CustomerCardsSummaryResponse response = new CustomerCardsSummaryResponse();
    response.setCustomerId(customerId);
    response.setCreditCards(creditCardMapper.toCreditCardSummaryList(creditCards));
    response.setDebitCards(debitCardMapper.toDebitCardSummaryList(debitCards));
    response.setTotalActiveCards(creditCards.size() + debitCards.size());
    response.setRetrievedAt(OffsetDateTime.now());
    return response;
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
        externalServiceWrapper.getDebitCardTransactionsSummaryWithCircuitBreaker(startDate, endDate)
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

  @Override
  public Mono<CardMovementsResponse> getCardMovements(String cardId, Integer limit) {
    Integer actualLimit = (limit != null && limit <= 50) ? limit : 10;
    log.debug("Getting last {} movements for card: {}", actualLimit, cardId);

    return determineCardType(cardId)
      .flatMap(cardType ->
        externalServiceWrapper.getLastCardMovementsWithCircuitBreaker(cardId, actualLimit)
          .collectList()
          .map(transactions -> buildCardMovementsResponse(cardId, cardType, transactions))
      )
      .doOnSuccess(response -> log.debug("Movements retrieved for card: {} with {} movements",
        cardId, response.getTotalCount()));
  }

  private Mono<String> determineCardType(String cardId) {
    return debitCardService.getCardById(cardId)
      .map(debitCard -> "DEBIT")
      .switchIfEmpty(
        creditCardService.getCardById(cardId)
          .map(creditCard -> "CREDIT")
          .switchIfEmpty(Mono.error(new CardServiceException(
            "CARD_NOT_FOUND",
            "Card not found: " + cardId,
            HttpStatus.NOT_FOUND)))
      );
  }

  private CardMovementsResponse buildCardMovementsResponse(
    String cardId,
    String cardType,
    List<TransactionResponse> transactions) {

    CardMovementsResponse response = new CardMovementsResponse();
    response.setCardId(cardId);
    response.setCardType(CardMovementsResponse.CardTypeEnum.valueOf(cardType));
    response.setMovements(cardMapper.toCardMovementList(transactions));
    response.setTotalCount(transactions.size());
    response.setRetrievedAt(OffsetDateTime.now());
    return response;
  }

}

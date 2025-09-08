package com.bootcamp.ntt.card_service.client;

import com.bootcamp.ntt.card_service.client.dto.transaction.*;
import com.bootcamp.ntt.card_service.exception.TransactionServiceException;
import com.bootcamp.ntt.card_service.model.TransactionCreateRequest;
import com.bootcamp.ntt.card_service.model.TransactionsSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceClient {

  private final WebClient webClient;

  @Value("${services.transaction.base-url:http://localhost:8082}")
  private String transactionServiceUrl;

  /**
   * Crea una transacción en el transaction-service
   * @param request Datos de la transacción a crear
   * @return Mono<Void> - Solo nos interesa que se complete exitosamente
   */
  public Mono<Void> createTransaction(TransactionCreateRequest request) {
    log.debug("Creating transaction for card: {}", request.getCardId());

    return webClient
      .post()
      .uri(transactionServiceUrl + "/transactions")
      .bodyValue(request)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("Invalid transaction request for card: {}", request.getCardId());
        return response.bodyToMono(String.class)
          .flatMap(errorBody -> {
            log.warn("Transaction service 4xx error: {}", errorBody);
            return Mono.error(new TransactionServiceException(
              "Invalid transaction request: " + errorBody));
          });
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Transaction service error for card: {}", request.getCardId());
        return response.bodyToMono(String.class)
          .flatMap(errorBody -> {
            log.error("Transaction service 5xx error: {}", errorBody);
            return Mono.error(new TransactionServiceException(
              "Error communicating with transaction service: " + errorBody));
          });
      })
      .bodyToMono(Void.class) // No necesitamos la respuesta, solo confirmar que se creó
      .timeout(java.time.Duration.ofSeconds(5)) // Timeout para evitar bloqueos
      .doOnSuccess(ignored -> log.debug("Transaction created successfully for card: {}", request.getCardId()))
      .doOnError(error -> log.error("Error creating transaction for card {}: {}",
        request.getCardId(), error.getMessage()));
  }

  /**
   * Obtiene resumen de transacciones de tarjetas de crédito en un período
   */
  public Mono<TransactionsSummary> getCreditCardTransactionsSummary(LocalDate startDate, LocalDate endDate) {
    log.debug("Getting credit card transactions summary from {} to {}", startDate, endDate);

    return webClient
      .get()
      .uri(transactionServiceUrl + "/transactions/summary/credit-cards?startDate={startDate}&endDate={endDate}",
        startDate, endDate)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("No transactions found for period: {} to {}", startDate, endDate);
        return Mono.error(new TransactionServiceException("No transactions found for period"));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Transaction service error getting summary");
        return Mono.error(new TransactionServiceException("Error getting transactions summary"));
      })
      .bodyToMono(TransactionsSummary.class)
      .doOnSuccess(summary -> log.debug("Retrieved transactions summary: {} transactions, total amount: {}",
        summary.getTotalTransactions(), summary.getTotalAmount()))
      .doOnError(error -> log.error("Error getting credit card transactions summary: {}", error.getMessage()));
  }

  /**
   * Obtiene resumen de transacciones de tarjetas de débito en un período
   */
  public Mono<TransactionsSummary> getDebitCardTransactionsSummary(LocalDate startDate, LocalDate endDate) {
    log.debug("Getting debit card transactions summary from {} to {}", startDate, endDate);

    return webClient
      .get()
      .uri(transactionServiceUrl + "/transactions/summary/debit-cards?startDate={startDate}&endDate={endDate}",
        startDate, endDate)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("No debit transactions found for period: {} to {}", startDate, endDate);
        return Mono.error(new TransactionServiceException("No debit transactions found for period"));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Transaction service error getting debit summary");
        return Mono.error(new TransactionServiceException("Error getting debit transactions summary"));
      })
      .bodyToMono(TransactionsSummary.class)
      .doOnSuccess(summary -> log.debug("Retrieved debit transactions summary: {} transactions, total amount: {}",
        summary.getTotalTransactions(), summary.getTotalAmount()))
      .doOnError(error -> log.error("Error getting debit card transactions summary: {}", error.getMessage()));
  }

  /**
   * Método auxiliar para crear el TransactionCreateRequest desde los datos del cargo
   */
  public static TransactionCreateRequest buildChargeRequest(String cardId, Double amount, String authCode) {
    TransactionCreateRequest request = new TransactionCreateRequest();
    request.setCardId(cardId);
    request.setAmount(amount);
    request.setTransactionType("CHARGE");
    request.setAuthorizationCode(authCode);
    request.setStatus("APPROVED");
    request.setTimestamp(OffsetDateTime.now());
    return request;
  }


  public Flux<TransactionResponse> getLastCardMovements(String cardId, Integer limit) {
    log.debug("Getting last {} movements for card: {}", limit, cardId);

    return webClient
      .get()
      .uri(transactionServiceUrl + "/transactions/cards/{cardId}?limit={limit}", cardId, limit)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("No transactions found for card: {}", cardId);
        return Mono.error(new TransactionServiceException("No transactions found"));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Transaction service error for card: {}", cardId);
        return Mono.error(new TransactionServiceException("Error getting transactions"));
      })
      .bodyToFlux(TransactionResponse.class)
      .doOnComplete(() -> log.debug("Transactions retrieved for card: {}", cardId))
      .doOnError(error -> log.error("Error getting transactions: {}", error.getMessage()));
  }
}

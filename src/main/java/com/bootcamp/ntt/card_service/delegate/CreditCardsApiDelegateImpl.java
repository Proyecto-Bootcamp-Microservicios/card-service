package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.CreditCardsApiDelegate;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCardsApiDelegateImpl implements CreditCardsApiDelegate {

  private final CardService cardService;

  /**
   * POST /cards : Create a new card
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> createCreditCard(
    Mono<CreditCardCreateRequest> cardRequest,
    ServerWebExchange exchange) {

    log.info("Creating new card - Request received");

    return cardRequest
      .doOnNext(request -> log.info("Creating card for customer: {}", request.getCustomerId()))
      .flatMap(cardService::createCard)
      .map(response -> {
        log.info("Card created successfully with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      });
  }

  /**
   * GET /cards : Get all cards
   */
  @Override
  public Mono<ResponseEntity<Flux<CreditCardResponse>>> getAllCreditCards(
    String customerId,
    Boolean isActive,
    ServerWebExchange exchange) {

    Boolean activeFilter = (isActive != null) ? isActive : true;

    Flux<CreditCardResponse> cards = (customerId != null)
      ? cardService.getCardsByActiveAndCustomer(activeFilter, customerId)
      : cardService.getCardsByActive(activeFilter);

    cards = cards.doOnComplete(() -> log.info("Cards retrieved successfully"));

    return Mono.just(ResponseEntity.ok(cards));
  }

  /**
   * GET /cards/{id} : Get a card by ID
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> getCreditCardById(
    String id,
    ServerWebExchange exchange) {

    log.info("Getting card by ID: {}", id);

    return cardService
      .getCardById(id)
      .map(response -> {
        log.info("Card found: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Card not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }));
  }

  /**
   * PUT /cards/{id} : Update a card
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> updateCreditCard(
    String id,
    Mono<CreditCardUpdateRequest> cardRequest,
    ServerWebExchange exchange) {

    log.info("Updating card with ID: {}", id);

    return cardRequest
      .doOnNext(request -> log.info("Update request for card ID: {}", id))
      .flatMap(request -> cardService.updateCard(id, request))
      .map(response -> {
        log.info("Card updated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * DELETE /cards/{id} : Delete a card
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteCreditCard(
    String id,
    ServerWebExchange exchange) {

    log.info("Deleting card with ID: {}", id);

    return cardService
      .deleteCard(id)
      .then(Mono.fromCallable(() -> {
        log.info("Card deleted successfully: {}", id);
        return ResponseEntity.noContent().build();
      }));
  }

  /**
   * PUT /cards/{id}/deactivate : Deactivate a card
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> deactivateCreditCard(
    String id,
    ServerWebExchange exchange) {

    log.info("Deactivating card with ID: {}", id);

    return cardService
      .deactivateCard(id)
      .map(response -> {
        log.info("Card deactivated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * PUT /cards/{id}/activate : Activate a card
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> activateCreditCard(
    String id,
    ServerWebExchange exchange) {

    log.info("Activating card with ID: {}", id);

    return cardService
      .activateCard(id)
      .map(response -> {
        log.info("Card activated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * POST /cards/{cardNumber}/authorizeCharge : Authorize a charge
   */
  @Override
  public Mono<ResponseEntity<ChargeAuthorizationResponse>> authorizeCharge(
    String cardNumber,
    Mono<ChargeAuthorizationRequest> chargeAuthorizationRequest,
    ServerWebExchange exchange) {

    log.info("Authorizing charge for card ID: {}", cardNumber);

    return chargeAuthorizationRequest
      .flatMap(request -> cardService.authorizeCharge(cardNumber, request))
      .map(response -> {
        log.info("Charge authorized for card {}", cardNumber);
        return ResponseEntity.ok(response);
      });
  }

  /**

   POST /cards/{cardNumber}/process-payment : Process card payment
   */
  @Override
  public Mono<ResponseEntity<PaymentProcessResponse>> processCardPayment(
    String cardNumber,
    Mono<PaymentProcessRequest> paymentProcessRequest,
    ServerWebExchange exchange) {
    log.info("Processing payment for card: {}", cardNumber);
    return paymentProcessRequest
      .doOnNext(request -> log.info("Payment request for card {}: amount {}", cardNumber, request.getAmount()))
      .flatMap(request -> cardService.processPayment(cardNumber, request))
      .map(response -> {
        if (response.getSuccess()) {
          log.info("Payment processed successfully for card {}: paid {}",
            cardNumber, response.getActualPaymentAmount());
        } else {
          log.warn("Payment failed for card {}: {}", cardNumber, response.getErrorMessage());
        }
        return ResponseEntity.ok(response);
      });
  }

  /**

   GET /cards/{cardNumber}/balance : Get card balance
   */
  @Override
  public Mono<ResponseEntity<CreditCardBalanceResponse>> getCardBalance(
    String cardNumber,
    ServerWebExchange exchange) {
    log.info("Getting balance for card: {}", cardNumber);
    return cardService.getCardBalance(cardNumber)
      .map(response -> {
        log.info("Balance retrieved for card {}: available {}, current {}",
          cardNumber, response.getAvailableCredit(), response.getCurrentBalance());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * GET /cards/customers/{customerId}/has-active-card : Check if customer has active card
   */
  @Override
  public Mono<ResponseEntity<CustomerCardValidationResponse>> checkCustomerHasActiveCard(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Checking active cards for customer: {}", customerId);

    return cardService
      .getCustomerCardValidation(customerId)
      .map(response -> {
        log.info("Customer validation completed for {}: hasActiveCard={}, count={}",
          customerId, response.getHasActiveCard(), response.getActiveCardCount());
        return ResponseEntity.ok(response);
      })
      .doOnError(error -> log.error("Error validating customer {}: {}", customerId, error.getMessage()));
  }

  /**
   * GET /cards/customers/{customerId}/daily-averages : Get customer daily averages
   */
  @Override
  public Mono<ResponseEntity<CustomerDailyAverageResponse>> getCustomerDailyAverages(
    String customerId,
    Integer year,
    Integer month,
    ServerWebExchange exchange) {

    log.info("Getting daily averages for customer: {} for {}/{}", customerId, month, year);

    return cardService
      .getCustomerDailyAverages(customerId, year, month)
      .map(response -> {
        log.info("Daily averages retrieved for customer {}: {} products found",
          customerId, response.getProducts().size());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("No daily averages found for customer: {} for {}/{}", customerId, month, year);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error getting daily averages for customer {}: {}", customerId, error.getMessage()));
  }
}

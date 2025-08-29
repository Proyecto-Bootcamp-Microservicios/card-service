package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.CardService;
import com.bootcamp.ntt.card_service.api.CardsApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardsApiDelegateImpl implements CardsApiDelegate {

  private final CardService cardService;

  /**
   * POST /credits : Create a new credit
   */
  @Override
  public Mono<ResponseEntity<CardResponse>> createCreditCard(
    Mono<CardCreateRequest> cardRequest,
    ServerWebExchange exchange) {

    log.info("Creating new card - Request received");

    return cardRequest
      .doOnNext(request -> log.info("Creating card for customer: {}", request.getCustomerId()))
      .flatMap(cardService::createCard)
      .map(response -> {
        log.info("Card created successfully with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      })
      .doOnError(error -> log.error("Error creating card: {}", error.getMessage(), error))
      .onErrorResume(this::handleError);
  }

  /**
   * GET /credits : Get all credits
   */
  @Override
  public Mono<ResponseEntity<Flux<CardResponse>>> getAllCreditCards(String customerId, Boolean isActive, ServerWebExchange exchange) {

    Boolean activeFilter = (isActive != null) ? isActive : true;

    Flux<CardResponse> cards;

    if (customerId != null) {
      cards = cardService.getCardsByActiveAndCustomer(activeFilter, customerId);
    } else {
      cards = cardService.getCardsByActive(activeFilter);
    }

    cards = cards
      .doOnComplete(() -> log.info("Cards retrieved successfully"))
      .doOnError(error -> log.error("Error getting cards: {}", error.getMessage(), error));

    return Mono.just(ResponseEntity.ok(cards))
      .onErrorResume(error -> {
        log.error("Exception in getAllCards: {}", error.getMessage(), error);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Flux.empty()));
      });
  }

  /**
   * GET /credits/{id} : Get a credit by ID
   */
  @Override
  public Mono<ResponseEntity<CardResponse>> getCreditCardById(
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
        log.warn("Cards not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error getting card by ID {}: {}", id, error.getMessage(), error))
      .onErrorResume(this::handleError);
  }

  /**
   * GET /credits/active?isActive=true : Get active credits
   */
  /*@Override
  public Mono<ResponseEntity<Flux<CardResponse>>> getActiveCredits(
    Boolean isActive,
    String customerId,
    ServerWebExchange exchange) {

    log.info("Getting credits with isActive: {}", isActive);

    Flux<CardResponse> credits = CardService
      .getActiveCredits(isActive)  // ✅ Método del service
      .doOnComplete(() -> log.info("Active credits retrieved successfully"))
      .doOnError(error -> log.error("Error getting active credits: {}", error.getMessage(), error));

    return Mono.just(ResponseEntity.ok(credits))
      .onErrorResume(error -> {
        log.error("Exception in getActiveCredits: {}", error.getMessage(), error);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Flux.empty()));
      });
  }*/

  /**
   * PUT /credits/{id} : Update a credit by ID
   */
  @Override
  public Mono<ResponseEntity<CardResponse>> updateCreditCard(
    String id,
    Mono<CardUpdateRequest> cardRequest,
    ServerWebExchange exchange) {

    log.info("Updating credit with ID: {}", id);

    return cardRequest
      .doOnNext(request -> log.info("Update request for card ID: {}", id))
      .flatMap(request -> cardService.updateCard(id, request))
      .map(response -> {
        log.info("Card updated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Card not found for update with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error updating card {}: {}", id, error.getMessage(), error))
      .onErrorResume(this::handleError);
  }

  /**
   * DELETE /credits/{id} : Delete a credit by ID
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteCreditCard(
    String id,
    ServerWebExchange exchange) {

    log.info("Deleting credit with ID: {}", id);

    return cardService
      .deleteCard(id)
      .then(Mono.just(ResponseEntity.noContent().<Void>build()))
      .doOnSuccess(response -> log.info("Card deleted successfully: {}", id))
      .onErrorResume(error -> {
        log.error("Error deleting card {}: {}", id, error.getMessage(), error);
        if (isNotFoundError(error)) {
          return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
      });
  }

  @Override
  public Mono<ResponseEntity<CardResponse>> deactivateCreditCard(
    String id,
    ServerWebExchange exchange) {

    log.info("Deactivating card with ID: {}", id);

    return cardService.deactivateCard(id)
      .map(response -> {
        log.info("Card deactivated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Card not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error deactivating card {}: {}", id, error.getMessage()))
      .onErrorResume(this::handleError);
  }

  @Override
  public Mono<ResponseEntity<CardResponse>> activateCreditCard(
    String id,
    ServerWebExchange exchange) {

    log.info("Activating card with ID: {}", id);

    return cardService.activateCard(id)
      .map(response -> {
        log.info("Card activated successfully: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Card not found with ID: {}", id);
        return ResponseEntity.notFound().build();
      }))
      .doOnError(error -> log.error("Error activating card {}: {}", id, error.getMessage()))
      .onErrorResume(this::handleError);
  }

  @Override
  public Mono<ResponseEntity<ChargeAuthorizationResponse>> authorizeCharge(
    String cardNumber,
    Mono<ChargeAuthorizationRequest> chargeAuthorizationRequest,
    ServerWebExchange exchange) {

    log.info("Authorizing charge for card ID: {}", cardNumber);

    log.info("Authorizing charge for card ID: {}", cardNumber);

    Mono<ChargeAuthorizationResponse> responseMono = chargeAuthorizationRequest
      .flatMap(request -> cardService.authorizeCharge(cardNumber, request))
      .onErrorResume(this::isInputValidationError, e -> {
        log.debug("Input validation error: {}", e.getMessage());
        return cardService.createInvalidAmountResponse();
      });

    return responseMono
      .map(ResponseEntity::ok)
      .doOnError(error -> log.error("Error authorizing charge for card {}: {}",
        cardNumber, error.getMessage(), error))
      .onErrorResume(this::handleErrorAuthorization);
  }

  private boolean isInputValidationError(Throwable error) {
    return error instanceof ServerWebInputException ||
      error instanceof DecodingException ||
      (error.getMessage() != null &&
        (error.getMessage().contains("validation") ||
          error.getMessage().contains("decode") ||
          error.getMessage().contains("format")));
  }


  /**
   * Manejo centralizado de errores para operaciones que retornan CardResponse
   */
  private Mono<ResponseEntity<CardResponse>> handleError(Throwable error) {
    log.error("Handling error: {}", error.getMessage(), error);

    if (isNotFoundError(error)) {
      return Mono.just(ResponseEntity.notFound().build());
    }

    if (isValidationError(error)) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
  }

  /**
   * Manejo centralizado de errores para operaciones que retornan ChargeAuthorizationResponse
   */
  private Mono<ResponseEntity<ChargeAuthorizationResponse>> handleErrorAuthorization(Throwable error) {
    log.error("Handling error: {}", error.getMessage(), error);

    if (isNotFoundError(error)) {
      return Mono.just(ResponseEntity.notFound().build());
    }

    if (isValidationError(error)) {
      return Mono.just(ResponseEntity.badRequest().build());
    }

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
  }

  /**
   * Verifica si el error es de tipo "not found"
   */
  private boolean isNotFoundError(Throwable error) {
    return error.getMessage() != null &&
      (error.getMessage().contains("not found") ||
        error.getMessage().contains("Not Found") ||
        error instanceof RuntimeException && error.getMessage().contains("404"));
  }

  /**
   * Verifica si el error es de validación
   */
  private boolean isValidationError(Throwable error) {
    return error.getMessage() != null &&
      (error.getMessage().contains("validation") ||
        error.getMessage().contains("invalid") ||
        error instanceof IllegalArgumentException);
  }

}

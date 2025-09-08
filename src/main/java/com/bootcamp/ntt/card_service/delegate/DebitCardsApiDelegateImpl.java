package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.DebitCardsApiDelegate;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.exception.EntityNotFoundException;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.DebitCardService;
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
public class DebitCardsApiDelegateImpl implements DebitCardsApiDelegate {

  private final DebitCardService debitCardService;

  /**
   * POST /debit-cards : Crear nueva tarjeta de débito
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> createDebitCard(
    Mono<DebitCardCreateRequest> cardRequest,
    ServerWebExchange exchange) {
    log.info("Creando nueva tarjeta de débito");
    return cardRequest
      .doOnNext(request -> log.info("Creando tarjeta para cliente: {}", request.getCustomerId()))
      .flatMap(debitCardService::createCard)
      .map(response -> {
        log.info("Tarjeta de débito creada con ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      });
  }

  /**
   * GET /debit-cards : Obtener todas las tarjetas de débito
   */
  @Override
  public Mono<ResponseEntity<Flux<DebitCardResponse>>> getAllDebitCards(
    String customerId,
    Boolean isActive,
    ServerWebExchange exchange) {
    Boolean activeFilter = (isActive != null) ? isActive : true;
    Flux<DebitCardResponse> cards = (customerId != null)
      ? debitCardService.getDebitCardsByActiveAndCustomer(activeFilter, customerId)
      : debitCardService.getDebitCardsByActive(activeFilter);
    cards = cards.doOnComplete(() -> log.info("Tarjetas de débito recuperadas correctamente"));
    return Mono.just(ResponseEntity.ok(cards));
  }

  /**
   * GET /debit-cards/{id} : Obtener tarjeta de débito por ID
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> getDebitCardById(
    String id,
    ServerWebExchange exchange) {
    log.info("Obteniendo tarjeta de débito por ID: {}", id);
    return debitCardService
      .getCardById(id)
      .map(response -> {
        log.info("Tarjeta encontrada: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Tarjeta de débito no encontrada con ID: {}", id);
        return ResponseEntity.notFound().build();
      }));
  }

  /**
   * PUT /debit-cards/{id} : Actualizar tarjeta de débito
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> updateDebitCard(
    String id,
    Mono<DebitCardUpdateRequest> cardRequest,
    ServerWebExchange exchange) {
    log.info("Actualizando tarjeta de débito con ID: {}", id);
    return cardRequest
      .doOnNext(request -> log.info("Solicitud de actualización para tarjeta de débito ID: {}", id))
      .flatMap(request -> debitCardService.updateCard(id, request))
      .map(response -> {
        log.info("Tarjeta de débito actualizada correctamente: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * DELETE /debit-cards/{id} : Eliminar tarjeta de débito
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteDebitCard(
    String id,
    ServerWebExchange exchange) {
    log.info("Eliminando tarjeta de débito con ID: {}", id);
    return debitCardService
      .deleteCard(id)
      .then(Mono.fromCallable(() -> {
        log.info("Tarjeta de débito eliminada correctamente: {}", id);
        return ResponseEntity.noContent().build();
      }));
  }

  /**
   * PATCH /debit-cards/{id}/deactivate : Desactivar tarjeta de débito
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> deactivateDebitCard(
    String id,
    ServerWebExchange exchange) {
    log.info("Desactivando tarjeta de débito con ID: {}", id);
    return debitCardService
      .deactivateCard(id)
      .map(response -> {
        log.info("Tarjeta de débito desactivada correctamente: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * PATCH /debit-cards/{id}/activate : Activar tarjeta de débito
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> activateDebitCard(
    String id,
    ServerWebExchange exchange) {
    log.info("Activando tarjeta de débito con ID: {}", id);
    return debitCardService
      .activateCard(id)
      .map(response -> {
        log.info("Tarjeta de débito activada correctamente: {}", response.getId());
        return ResponseEntity.ok(response);
      });
  }

  @Override
  public Mono<ResponseEntity<DebitCardResponse>> associateAccountToDebitCard(
    String id,
    Mono<AssociateAccountRequest> associateAccountRequest,
    ServerWebExchange exchange) {

    log.info("Associating account to debit card ID: {}", id);

    return associateAccountRequest
      .flatMap(request -> debitCardService.associateAccountToDebitCard(id, request))
      .map(response -> {
        log.info("Account associated successfully to debit card: {}", id);
        return ResponseEntity.ok(response);
      });
  }

  @Override
  public Mono<ResponseEntity<DebitPurchaseResponse>> processDebitCardPurchase(
    String cardNumber,
    Mono<DebitPurchaseRequest> debitPurchaseRequest,
    ServerWebExchange exchange) {

    log.info("Processing debit card transaction for card: {}", cardNumber);

    return debitPurchaseRequest
      .flatMap(request -> debitCardService.processDebitCardPurchase(cardNumber, request))
      .map(response -> {
        log.info("Debit transaction processed successfully for card: {}", cardNumber);
        return ResponseEntity.ok(response);
      })
      .onErrorResume(error -> {
        log.error("Error processing debit transaction for card {}: {}", cardNumber, error.getMessage());

        if (error instanceof EntityNotFoundException) {
          return Mono.just(ResponseEntity.notFound().build());
        } else if (error instanceof BusinessRuleException) {
          return Mono.just(ResponseEntity.badRequest().build());
        }

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
      });
  }
}

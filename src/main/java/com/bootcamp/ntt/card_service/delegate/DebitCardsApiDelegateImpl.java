package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.DebitCardsApiDelegate;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.exception.EntityNotFoundException;
import com.bootcamp.ntt.card_service.mapper.DebitCardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.DebitCardService;

import com.bootcamp.ntt.card_service.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Implementación del delegate para la API de tarjetas de débito.
 * Proporciona endpoints para el manejo completo del ciclo de vida de tarjetas de débito,
 * incluyendo creación, consulta, actualización, activación/desactivación, asociación de cuentas,
 * procesamiento de compras y consulta de balances de cuenta primaria.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitCardsApiDelegateImpl implements DebitCardsApiDelegate {

  private final DebitCardService debitCardService;
  private final DebitCardMapper debitCardMapper;
  private final SecurityUtils securityUtils;
  /**
   * Crea una nueva tarjeta de débito para un cliente.
   * Valida los datos del cliente y genera una nueva tarjeta con número único
   * que debe estar asociada a una cuenta bancaria existente.
   *
   * @param cardRequest Datos de la tarjeta a crear (customerId, accountId, etc.)
   * @param exchange    Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta de débito creada o error de validación
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> createDebitCard(
    Mono<DebitCardCreateRequest> cardRequest,
    ServerWebExchange exchange) {
    log.info("Creando nueva tarjeta de débito");
    return securityUtils.extractAuthHeaders(exchange)
      .doOnNext(auth -> log.debug(" Auth extracted - customerId: {}, isAdmin: {}",
        auth.getCustomerId(), auth.isAdmin()))
      .zipWith(cardRequest.doOnNext(req -> log.debug(" Original request customerId: {}",
        req.getCustomerId())))
      .flatMap(tuple -> {
        var auth = tuple.getT1();
        var request = tuple.getT2();

        DebitCardCreateRequest securedRequest = debitCardMapper.secureCreateRequest(
          request,
          auth.getCustomerId(),
          auth.isAdmin()
        );

        return debitCardService.createCard(securedRequest);
      })
      .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    /*
    return cardRequest
      .doOnNext(request -> log.info("Creando tarjeta para cliente: {}", request.getCustomerId()))
      .flatMap(debitCardService::createCard)
      .map(response -> {
        log.info("Tarjeta de débito creada con ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      });*/
  }

  /**
   * Obtiene todas las tarjetas de débito con filtros opcionales.
   * Permite filtrar por cliente específico y estado activo/inactivo.
   *
   * @param customerId ID del cliente para filtrar (opcional)
   * @param isActive   Estado de la tarjeta - true para activas, false para inactivas (por defecto: true)
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene el flujo de tarjetas de débito encontradas
   */
  @Override
  public Mono<ResponseEntity<Flux<DebitCardResponse>>> getAllDebitCards(
    String customerId,
    Boolean isActive,
    ServerWebExchange exchange) {
    log.info("Recuperando tarjetas de débito");
    return securityUtils.extractAuthHeaders(exchange)
      .map(auth -> {
        Boolean activeFilter = Optional.ofNullable(isActive).orElse(true);
        String resolvedCustomerId = auth.isAdmin() ? customerId : auth.getCustomerId();

        Flux<DebitCardResponse> cards = (resolvedCustomerId != null)
          ? debitCardService.getDebitCardsByActiveAndCustomer(activeFilter, resolvedCustomerId)
          : debitCardService.getDebitCardsByActive(activeFilter);

        return ResponseEntity.ok(cards);
      });

    /*Boolean activeFilter = (isActive != null) ? isActive : true;
    Flux<DebitCardResponse> cards = (customerId != null)
      ? debitCardService.getDebitCardsByActiveAndCustomer(activeFilter, customerId)
      : debitCardService.getDebitCardsByActive(activeFilter);
    cards = cards.doOnComplete(() -> log.info("Tarjetas de débito recuperadas correctamente"));
    return Mono.just(ResponseEntity.ok(cards));*/
  }

  /**
   * Obtiene una tarjeta de débito específica por su ID.
   *
   * @param id       ID único de la tarjeta de débito
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta encontrada o 404 si no existe
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> getDebitCardById(
    String id,
    ServerWebExchange exchange) {
    log.info("Obteniendo tarjeta de débito por ID: {}", id);
    return securityUtils.validateReadAccess(
        debitCardService.getCardById(id),
        DebitCardResponse::getCustomerId,
        exchange)
      .map(ResponseEntity::ok)
      .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    /*
    return debitCardService
      .getCardById(id)
      .map(response -> {
        log.info("Tarjeta encontrada: {}", response.getId());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Tarjeta de débito no encontrada con ID: {}", id);
        return ResponseEntity.notFound().build();
      }));*/
  }

  /**
   * Actualiza los datos de una tarjeta de débito existente.
   * Permite modificar información como límites de retiro y otros datos configurables.
   *
   * @param id          ID de la tarjeta a actualizar
   * @param cardRequest Datos actualizados de la tarjeta
   * @param exchange    Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta actualizada
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> updateDebitCard(
    String id,
    Mono<DebitCardUpdateRequest> cardRequest,
    ServerWebExchange exchange) {
    log.info("Actualizando tarjeta de débito con ID: {}", id);
    return securityUtils.validateAdminOnly(exchange)
      .then(cardRequest)
      .flatMap(request -> debitCardService.updateCard(id, request))
      .map(ResponseEntity::ok);

    /*
    return cardRequest
      .doOnNext(request -> log.info("Solicitud de actualización para tarjeta de débito ID: {}", id))
      .flatMap(request -> debitCardService.updateCard(id, request))
      .map(response -> {
        log.info("Tarjeta de débito actualizada correctamente: {}", response.getId());
        return ResponseEntity.ok(response);
      });*/
  }

  /**
   * Elimina una tarjeta de débito del sistema.
   * Esta operación es irreversible y debe usarse con precaución.
   *
   * @param id       ID de la tarjeta a eliminar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity vacío (204 No Content) si la eliminación fue exitosa
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteDebitCard(
    String id,
    ServerWebExchange exchange) {
    return securityUtils.validateAdminOnly(exchange)
      .then(debitCardService.deleteCard(id))
      .thenReturn(ResponseEntity.noContent().build());
    /*log.info("Eliminando tarjeta de débito con ID: {}", id);
    return debitCardService
      .deleteCard(id)
      .then(Mono.fromCallable(() -> {
        log.info("Tarjeta de débito eliminada correctamente: {}", id);
        return ResponseEntity.noContent().build();
      }));*/
  }

  /**
   * Desactiva una tarjeta de débito, impidiendo nuevas transacciones.
   * La tarjeta mantiene su historial pero no puede ser usada para compras o retiros.
   *
   * @param id       ID de la tarjeta a desactivar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta desactivada
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> deactivateDebitCard(
    String id,
    ServerWebExchange exchange) {
    log.info("Desactivando tarjeta de débito con ID: {}", id);
    return securityUtils.validateAdminOnly(exchange)
      .then(debitCardService.deactivateCard(id))
      .map(ResponseEntity::ok);
    /*return debitCardService
      .deactivateCard(id)
      .map(response -> {
        log.info("Tarjeta de débito desactivada correctamente: {}", response.getId());
        return ResponseEntity.ok(response);
      });*/
  }

  /**
   * Activa una tarjeta de débito, permitiendo realizar transacciones.
   * Solo las tarjetas activas pueden procesar compras, retiros y consultas de saldo.
   *
   * @param id       ID de la tarjeta a activar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta activada
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> activateDebitCard(
    String id,
    ServerWebExchange exchange) {
    log.info("Activando tarjeta de débito con ID: {}", id);
    return securityUtils.validateAdminOnly(exchange)
      .then(debitCardService.activateCard(id))
      .map(ResponseEntity::ok);
    /*return debitCardService
      .activateCard(id)
      .map(response -> {
        log.info("Tarjeta de débito activada correctamente: {}", response.getId());
        return ResponseEntity.ok(response);
      });*/
  }

  /**
   * Asocia una cuenta bancaria a una tarjeta de débito existente.
   * Permite vincular la tarjeta con cuentas adicionales del mismo cliente
   * para realizar transacciones desde diferentes fuentes de fondos.
   *
   * @param id                       ID de la tarjeta de débito
   * @param associateAccountRequest  Datos de la cuenta a asociar (accountId, tipo de asociación)
   * @param exchange                 Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta con la nueva asociación de cuenta
   */
  @Override
  public Mono<ResponseEntity<DebitCardResponse>> associateAccountToDebitCard(
    String id,
    Mono<AssociateAccountRequest> associateAccountRequest,
    ServerWebExchange exchange) {

    log.info("Associating account to debit card ID: {}", id);
    return securityUtils.validateReadAccess(
        debitCardService.getCardById(id),
        DebitCardResponse::getCustomerId,
        exchange)
      .then(associateAccountRequest)
      .flatMap(request -> debitCardService.associateAccountToDebitCard(id, request))
      .map(response -> {
        log.info("Account associated successfully to debit card: {}", id);
        return ResponseEntity.ok(response);
      });
    /*return associateAccountRequest
      .flatMap(request -> debitCardService.associateAccountToDebitCard(id, request))
      .map(response -> {
        log.info("Account associated successfully to debit card: {}", id);
        return ResponseEntity.ok(response);
      });*/
  }

  /**
   * Procesa una compra con tarjeta de débito.
   * Valida que la tarjeta esté activa, que tenga fondos suficientes en la cuenta asociada
   * y ejecuta el débito correspondiente.
   *
   * @param cardNumber             Número de la tarjeta de débito para la compra
   * @param debitPurchaseRequest   Datos de la compra (monto, comercio, descripción, etc.)
   * @param exchange               Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la respuesta del procesamiento de la compra
   */
  @Override
  public Mono<ResponseEntity<DebitPurchaseResponse>> processDebitCardPurchase(
    String cardNumber,
    Mono<DebitPurchaseRequest> debitPurchaseRequest,
    ServerWebExchange exchange) {

    log.info("Processing debit card transaction for card: {}", cardNumber);

    return securityUtils.validateReadAccess(
        debitCardService.getDebitCardByCardNumber(cardNumber),
        DebitCardResponse::getCustomerId,
        exchange)
      .then(debitPurchaseRequest)
      .flatMap(request -> debitCardService.processDebitCardPurchase(cardNumber, request))
      .map(response -> {
        log.info("Debit transaction processed successfully for card: {}", cardNumber);
        return ResponseEntity.ok(response);
      });
    /*return debitPurchaseRequest
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
      });*/
  }

  /**
   * Obtiene el balance de la cuenta primaria asociada a una tarjeta de débito.
   * Consulta directamente en el servicio de cuentas el saldo disponible
   * en la cuenta principal vinculada a la tarjeta.
   *
   * @param cardId   ID de la tarjeta de débito
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la información del balance de la cuenta primaria
   */
  @Override
  public Mono<ResponseEntity<PrimaryAccountBalanceResponse>> getDebitCardPrimaryAccountBalance(
    String cardId,
    ServerWebExchange exchange) {

    log.info("Getting primary account balance for debit card: {}", cardId);
    return securityUtils.validateReadAccess(
        debitCardService.getCardById(cardId),
        DebitCardResponse::getCustomerId,
        exchange)
      .then(debitCardService.getDebitCardPrimaryAccountBalance(cardId))
      .map(ResponseEntity::ok);
  }
    /*return debitCardService.getDebitCardPrimaryAccountBalance(cardId)
      .map(ResponseEntity::ok);*/

}

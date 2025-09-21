package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.CreditCardsApiDelegate;
import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.exception.AccessDeniedException;
import com.bootcamp.ntt.card_service.mapper.CreditCardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.CreditCardService;

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
 * Implementación del delegate para la API de tarjetas de crédito.
 * Proporciona endpoints para el manejo completo del ciclo de vida de tarjetas de crédito,
 * incluyendo creación, consulta, actualización, activación/desactivación, autorización de cargos,
 * procesamiento de pagos y consulta de balances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCardsApiDelegateImpl implements CreditCardsApiDelegate {

  private final CreditCardService creditCardService;
  private final SecurityUtils securityUtils;
  private final CreditCardMapper creditCardMapper;

  /**
   * Crea una nueva tarjeta de crédito para un cliente.
   * Valida los datos del cliente y genera una nueva tarjeta con número único.
   *
   * @param cardRequest Datos de la tarjeta a crear (customerId, creditCardType, creditLimit)
   * @param exchange    Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta creada o error de validación
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> createCreditCard(
    Mono<CreditCardCreateRequest> cardRequest,
    ServerWebExchange exchange) {

    return securityUtils.extractAuthHeaders(exchange)
      .doOnNext(auth -> log.debug(" Auth extracted - customerId: {}, isAdmin: {}",
        auth.getCustomerId(), auth.isAdmin()))
      .zipWith(cardRequest.doOnNext(req -> log.debug(" Original request customerId: {}",
        req.getCustomerId())))
      .flatMap(tuple -> {
        var auth = tuple.getT1();
        var request = tuple.getT2();

        CreditCardCreateRequest securedRequest = creditCardMapper.secureCreateRequest(
          request,
          auth.getCustomerId(),
          auth.isAdmin()
        );

        return creditCardService.createCard(securedRequest);
      })
      .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
  }

  /**
   * Obtiene todas las tarjetas de crédito con filtros opcionales.
   * Permite filtrar por cliente específico y estado activo/inactivo.
   *
   * @param customerId ID del cliente para filtrar (opcional)
   * @param isActive   Estado de la tarjeta - true para activas, false para inactivas (por defecto: true)
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene el flujo de tarjetas encontradas
   */
  @Override
  public Mono<ResponseEntity<Flux<CreditCardResponse>>> getAllCreditCards(
    String customerId, Boolean isActive, ServerWebExchange exchange) {

    return securityUtils.extractAuthHeaders(exchange)
      .map(auth -> {
        Boolean activeFilter = Optional.ofNullable(isActive).orElse(true);
        String resolvedCustomerId = auth.isAdmin() ? customerId : auth.getCustomerId();

        Flux<CreditCardResponse> cards = (resolvedCustomerId != null)
          ? creditCardService.getCardsByActiveAndCustomer(activeFilter, resolvedCustomerId)
          : creditCardService.getCardsByActive(activeFilter);

        return ResponseEntity.ok(cards);
      });
  }

  /**
   * Obtiene una tarjeta de crédito específica por su ID.
   *
   * @param id       ID único de la tarjeta
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta encontrada o 404 si no existe
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> getCreditCardById(String id, ServerWebExchange exchange) {
    return securityUtils.validateReadAccessGeneric(
        creditCardService.getCardById(id),
        CreditCardResponse::getCustomerId,
        exchange)
      .map(ResponseEntity::ok)
      .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
  }

  /**
   * Actualiza los datos de una tarjeta de crédito existente.
   * Permite modificar límite de crédito y otros datos configurables.
   *
   * @param id          ID de la tarjeta a actualizar
   * @param cardRequest Datos actualizados de la tarjeta
   * @param exchange    Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta actualizada
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> updateCreditCard(
    String id, Mono<CreditCardUpdateRequest> cardRequest, ServerWebExchange exchange) {

    return securityUtils.validateAdminOnly(exchange)
      .then(cardRequest)
      .flatMap(request -> creditCardService.updateCard(id, request))
      .map(ResponseEntity::ok);
  }

  /**
   * Elimina una tarjeta de crédito del sistema.
   * Esta operación es irreversible y debe usarse con precaución.
   *
   * @param id       ID de la tarjeta a eliminar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity vacío (204 No Content) si la eliminación fue exitosa
   */
  @Override
  public Mono<ResponseEntity<Void>> deleteCreditCard(String id, ServerWebExchange exchange) {
    return securityUtils.validateAdminOnly(exchange)
      .then(creditCardService.deleteCard(id))
      .thenReturn(ResponseEntity.noContent().build());
  }

  /**
   * Desactiva una tarjeta de crédito, impidiendo nuevas transacciones.
   * La tarjeta mantiene su historial pero no puede ser usada para cargos.
   *
   * @param id       ID de la tarjeta a desactivar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta desactivada
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> deactivateCreditCard(String id, ServerWebExchange exchange) {
    return securityUtils.validateAdminOnly(exchange)
      .then(creditCardService.deactivateCard(id))
      .map(ResponseEntity::ok);
  }

  /**
   * Activa una tarjeta de crédito, permitiendo realizar transacciones.
   * Solo las tarjetas activas pueden procesar cargos y pagos.
   *
   * @param id       ID de la tarjeta a activar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la tarjeta activada
   */
  @Override
  public Mono<ResponseEntity<CreditCardResponse>> activateCreditCard(String id, ServerWebExchange exchange) {
    return securityUtils.validateAdminOnly(exchange)
      .then(creditCardService.activateCard(id))
      .map(ResponseEntity::ok);
  }

  /**
   * Autoriza un cargo en una tarjeta de crédito.
   * Valida que la tarjeta esté activa, tenga crédito disponible y procesa la autorización.
   *
   * @param cardNumber                   Número de la tarjeta para el cargo
   * @param chargeAuthorizationRequest   Datos del cargo (monto, descripción, etc.)
   * @param exchange                     Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la respuesta de autorización
   */
  @Override
  public Mono<ResponseEntity<ChargeAuthorizationResponse>> authorizeCharge(
    String cardNumber,
    Mono<ChargeAuthorizationRequest> chargeAuthorizationRequest,
    ServerWebExchange exchange) {

    log.info("Authorizing charge for card: {}", cardNumber);

    return creditCardService.getCardByCardNumber(cardNumber)
      .flatMap(card -> securityUtils.validateReadAccess(card.getCustomerId(), exchange)
        .thenReturn(card))
      .flatMap(card -> chargeAuthorizationRequest
        .flatMap(request -> creditCardService.authorizeCharge(cardNumber, request)))
      .map(response -> {
        log.info("Charge authorized for card: {}", cardNumber);
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Card not found: {}", cardNumber);
        return ResponseEntity.notFound().build();
      }));
  }

  /**
   * Procesa un pago hacia una tarjeta de crédito.
   * Reduce el saldo pendiente de la tarjeta y aumenta el crédito disponible.
   *
   * @param cardNumber               Número de la tarjeta para el pago
   * @param paymentProcessRequest    Datos del pago (monto, método, etc.)
   * @param exchange                 Contexto del servidor web
   * @return Mono con ResponseEntity que contiene el resultado del procesamiento del pago
   */
  @Override
  public Mono<ResponseEntity<PaymentProcessResponse>> processCardPayment(
    String cardNumber,
    Mono<PaymentProcessRequest> paymentProcessRequest,
    ServerWebExchange exchange) {
    log.info("Processing payment for card: {}", cardNumber);
    return paymentProcessRequest
      .doOnNext(request -> log.info("Payment request for card {}: amount {}", cardNumber, request.getAmount()))
      .flatMap(request -> creditCardService.processPayment(cardNumber, request))
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
   * Obtiene el balance actual de una tarjeta de crédito.
   * Incluye el saldo pendiente, crédito disponible y límite total.
   *
   * @param cardNumber Número de la tarjeta
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la información del balance
   */
  @Override
  public Mono<ResponseEntity<CreditCardBalanceResponse>> getCardBalance(
    String cardNumber,
    ServerWebExchange exchange) {

    log.info("Getting balance for card: {}", cardNumber);

    return creditCardService.getCardBalance(cardNumber)
      .flatMap(balanceResponse -> creditCardService.getCardByCardNumber(cardNumber)
        .flatMap(card -> securityUtils.validateReadAccess(card.getCustomerId(), exchange)
          .thenReturn(balanceResponse)))
      .map(response -> {
        log.info("Balance retrieved for card {}: available {}, current {}",
          cardNumber, response.getAvailableCredit(), response.getCurrentBalance());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.fromCallable(() -> {
        log.warn("Card not found: {}", cardNumber);
        return ResponseEntity.notFound().build();
      }));
  }

  /**
   * Verifica si un cliente tiene al menos una tarjeta de crédito activa.
   * Útil para validaciones de negocio que requieren existencia de tarjetas activas.
   *
   * @param customerId ID del cliente a validar
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la información de validación del cliente
   */
  @Override
  public Mono<ResponseEntity<CustomerCardValidationResponse>> checkCustomerHasActiveCard(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Checking active cards for customer: {}", customerId);

    return securityUtils.validateReadAccessGeneric(
        creditCardService.getCustomerCardValidation(customerId),
        CustomerCardValidationResponse::getCustomerId,
        exchange
      )
      .map(response -> {
        log.info("Customer validation completed for {}: hasActiveCard={}, count={}",
          customerId, response.getHasActiveCard(), response.getActiveCardCount());
        return ResponseEntity.ok(response);
      })
      .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
  }

  /**
   * Obtiene los promedios diarios de saldos de tarjetas de un cliente para un mes específico.
   * Calcula estadísticas de uso y balance promedio por día del mes consultado.
   *
   * @param customerId ID del cliente
   * @param year       Año de consulta
   * @param month      Mes de consulta (1-12)
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene los promedios diarios o 404 si no hay datos
   */
  @Override
  public Mono<ResponseEntity<CustomerDailyAverageResponse>> getCustomerDailyAverages(
    String customerId,
    Integer year,
    Integer month,
    ServerWebExchange exchange) {

    log.info("Getting daily averages for customer: {} for {}/{}", customerId, month, year);

    return securityUtils.validateAdminOnly(exchange)
      .then(
        creditCardService.getCustomerDailyAverages(customerId, year, month)
          .map(response -> {
            log.info("Daily averages retrieved for customer {}: {} products found",
              customerId, response.getProducts().size());
            return ResponseEntity.ok(response);
          })
          .switchIfEmpty(Mono.fromCallable(() -> {
            log.warn("No daily averages found for customer: {} for {}/{}", customerId, month, year);
            return ResponseEntity.notFound().build();
          }))
      );
  }
  /**
   * Verifica la elegibilidad de un cliente para obtener nuevos productos de crédito
   * Evalúa si no tiene ningún producto de crédito vencido.
   * @param customerId ID único del cliente a evaluar
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la información de elegibilidad del cliente
   */
  @Override
  public Mono<ResponseEntity<ProductEligibilityResponse>> checkCustomerProductEligibility(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Checking product eligibility for customer ID: {}", customerId);

    return securityUtils.validateReadAccess(customerId, exchange)
      .then(creditCardService.checkCustomerProductEligibility(customerId))
      .map(response -> {
        log.info("Eligibility checked for customer: {} - Eligible: {}",
          customerId, response.getIsEligible());
        return ResponseEntity.ok(response);
      });
  }
}

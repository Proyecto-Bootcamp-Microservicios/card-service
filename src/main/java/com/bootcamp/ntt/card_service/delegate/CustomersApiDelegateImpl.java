package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.CustomersApiDelegate;
import com.bootcamp.ntt.card_service.model.CustomerCardsSummaryResponse;
import com.bootcamp.ntt.card_service.model.ProductEligibilityResponse;
import com.bootcamp.ntt.card_service.service.CardConsolidationService;
import com.bootcamp.ntt.card_service.service.CreditCardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Implementación del delegate para la API de clientes relacionada con tarjetas.
 * Proporciona endpoints para consultar información consolidada de tarjetas por cliente,
 * incluyendo resúmenes de tarjetas y verificación de elegibilidad para nuevos productos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomersApiDelegateImpl implements CustomersApiDelegate {

  private final CardConsolidationService cardConsolidationService;
  private final CreditCardService creditCardService;

  /**
   * Obtiene un resumen consolidado de todas las tarjetas de un cliente.
   * Incluye información de tarjetas de crédito y débito, balances totales,
   * límites de crédito y estado de las tarjetas.
   *
   * @param customerId ID único del cliente
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene el resumen consolidado de tarjetas del cliente
   */
  @Override
  public Mono<ResponseEntity<CustomerCardsSummaryResponse>> getCustomerCardsSummary(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Getting cards summary for customer ID: {}", customerId);

    return cardConsolidationService
      .getCustomerCardsSummary(customerId)
      .map(response -> {
        log.info("Cards summary retrieved successfully for customer: {} with {} total cards",
          customerId, response.getTotalActiveCards());
        return ResponseEntity.ok(response);
      });
  }

  /**
   * Verifica la elegibilidad de un cliente para obtener nuevos productos financieros.
   * Evalúa criterios como historial crediticio, número de tarjetas activas,
   * comportamiento de pago y políticas de negocio para determinar si el cliente
   * puede acceder a nuevas tarjetas de crédito o débito.
   *
   * @param customerId ID único del cliente a evaluar
   * @param exchange   Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la información de elegibilidad del cliente
   */
  @Override
  public Mono<ResponseEntity<ProductEligibilityResponse>> checkCustomerProductEligibility(
    String customerId,
    ServerWebExchange exchange) {

    log.info("Checking product eligibility for customer ID: {}", customerId);

    return creditCardService
      .checkCustomerProductEligibility(customerId)
      .map(response -> {
        log.info("Eligibility checked for customer: {} - Eligible: {}",
          customerId, response.getIsEligible());
        return ResponseEntity.ok(response);
      });
  }
}

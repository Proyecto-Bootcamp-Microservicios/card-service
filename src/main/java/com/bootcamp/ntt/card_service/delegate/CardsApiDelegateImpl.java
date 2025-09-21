package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.CardsApiDelegate;
import com.bootcamp.ntt.card_service.model.CardMovementsResponse;
import com.bootcamp.ntt.card_service.model.CardsPeriodicReportResponse;
import com.bootcamp.ntt.card_service.model.CustomerCardsSummaryResponse;
import com.bootcamp.ntt.card_service.service.CardConsolidationService;

import java.time.LocalDate;

import com.bootcamp.ntt.card_service.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Delegate para la API de consolidación de tarjetas.
 * Expone el endpoint para generar reportes periódicos de tarjetas (crédito y débito).
 * Llama al servicio CardConsolidationService para obtener el reporte consolidado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardsApiDelegateImpl implements CardsApiDelegate {

  private final CardConsolidationService cardConsolidationService;
  private final SecurityUtils securityUtils;

  /**
   * Genera un reporte periódico consolidado de tarjetas (crédito y débito) para el rango de fechas especificado.
   *
   * @param startDate Fecha de inicio del periodo (inclusive)
   * @param endDate   Fecha de fin del periodo (inclusive)
   * @param exchange  Contexto del servidor web
   * @return Mono con ResponseEntity que contiene el reporte consolidado o error
   */
  @Override
  public Mono<ResponseEntity<CardsPeriodicReportResponse>> generateCardsPeriodicReport(
    LocalDate startDate,
    LocalDate endDate,
    ServerWebExchange exchange) {

    log.info("Generating cards periodic report from {} to {}", startDate, endDate);

    return securityUtils.validateAdminOnly(exchange)
      .then(cardConsolidationService.generateCardsPeriodicReport(startDate, endDate))
        .map(response -> {
          log.info("Cards periodic report generated successfully for period {} to {}", startDate, endDate);
          return ResponseEntity.ok(response);
        });
  }

  /**
   * Obtiene los movimientos más recientes de una tarjeta específica.
   *
   * @param cardId   Identificador único de la tarjeta
   * @param limit    Número máximo de movimientos a retornar
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene la lista de movimientos de la tarjeta
   */
  @Override
  public Mono<ResponseEntity<CardMovementsResponse>> getCardMovements(
    String cardId,
    Integer limit,
    ServerWebExchange exchange) {

    log.info("Getting movements for card: {} with limit: {}", cardId, limit);
    return securityUtils.validateAdminOnly(exchange)
      .then(cardConsolidationService.getCardMovements(cardId, limit))
      .map(response -> {
        log.info("Cards movements report generated successfully for card {}", cardId);
        return ResponseEntity.ok(response);
      });
  }

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

    return securityUtils.validateAdminOnly(exchange)
      .then(cardConsolidationService.getCustomerCardsSummary(customerId))
      .map(response -> {
        log.info("Cards summary retrieved successfully for customer: {} with {} total cards",
          customerId, response.getTotalActiveCards());
        return ResponseEntity.ok(response);
      });
  }
}

package com.bootcamp.ntt.card_service.delegate;

import com.bootcamp.ntt.card_service.api.CardsApiDelegate;
import com.bootcamp.ntt.card_service.api.CreditCardsApiDelegate;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.service.CardConsolidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

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

  /**
   * Genera un reporte periódico consolidado de tarjetas (crédito y débito) para el rango de fechas especificado.
   *
   * @param startDate Fecha de inicio del periodo (inclusive)
   * @param endDate Fecha de fin del periodo (inclusive)
   * @param exchange Contexto del servidor web
   * @return Mono con ResponseEntity que contiene el reporte consolidado o error
   */
  @Override
  public Mono<ResponseEntity<CardsPeriodicReportResponse>> generateCardsPeriodicReport(
    LocalDate startDate,
    LocalDate endDate,
    ServerWebExchange exchange) {

    log.info("Generating cards periodic report from {} to {}", startDate, endDate);

    return cardConsolidationService.generateCardsPeriodicReport(startDate, endDate)
      .map(response -> {
        log.info("Cards periodic report generated successfully for period {} to {}", startDate, endDate);
        return ResponseEntity.ok(response);
      })
      .onErrorResume(error -> {
        log.error("Error generating cards periodic report: {}", error.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
      });
  }
}

package com.bootcamp.ntt.card_service.scheduler;

import com.bootcamp.ntt.card_service.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyBalanceScheduler {

  private final CardService cardService;

  // Todos los días a las 23:59
  @Scheduled(cron = "0 59 23 * * *")
  public void captureDailyBalances() {
    log.info("Starting scheduled daily balance capture");

    cardService.captureAllDailyBalances()
      .subscribe(
        null, // onNext no aplica para Mono<Void>
        error -> log.error("Scheduled balance capture failed: {}", error.getMessage()),
        () -> log.info("Scheduled daily balance capture completed")
      );
  }
}

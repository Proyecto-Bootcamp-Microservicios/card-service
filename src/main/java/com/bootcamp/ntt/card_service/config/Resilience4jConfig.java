package com.bootcamp.ntt.card_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class Resilience4jConfig {

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final TimeLimiterRegistry timeLimiterRegistry;

  @Bean
  public CircuitBreaker customerServiceCircuitBreaker() {
    return circuitBreakerRegistry.circuitBreaker("customer-service");
  }

  @Bean
  public CircuitBreaker transactionServiceCircuitBreaker() {
    return circuitBreakerRegistry.circuitBreaker("transaction-service");
  }

  @Bean
  public TimeLimiter customerServiceTimeLimiter() {
    return timeLimiterRegistry.timeLimiter("customer-service");
  }

  @Bean
  public TimeLimiter transactionServiceTimeLimiter() {
    return timeLimiterRegistry.timeLimiter("transaction-service");
  }
}

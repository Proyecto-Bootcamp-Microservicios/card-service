package com.bootcamp.ntt.card_service.utils;

import com.bootcamp.ntt.card_service.exception.AccessDeniedException;
import com.bootcamp.ntt.card_service.security.AuthHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Component
public class SecurityUtils {

  public Mono<AuthHeaders> extractAuthHeaders(ServerWebExchange exchange) {
    return Mono.fromCallable(() -> {
      String customerId = exchange.getRequest().getHeaders().getFirst("X-Customer-Id");
      String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");
      String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

      return new AuthHeaders(customerId, userRole, userId);
    });
  }

  public Mono<Void> validateAdminOnly(ServerWebExchange exchange) {
    return extractAuthHeaders(exchange)
      .flatMap(auth -> {
        if (auth.isAdmin()) {
          return Mono.empty();
        }
        return Mono.error(new AccessDeniedException("Admin role required"));
      });
  }
  public Mono<Void> validateReadAccess(String resourceCustomerId, ServerWebExchange exchange) {
    return extractAuthHeaders(exchange)
      .flatMap(auth -> {
        if (auth.isAdmin() || auth.hasCustomerId(resourceCustomerId)) {
          return Mono.empty();
        }
        return Mono.error(new AccessDeniedException("Access denied to resource"));
      });
  }

  /**
   * Método genérico para validar acceso de lectura
   */
  public <T> Mono<T> validateReadAccessGeneric(Mono<T> resourceMono, Function<T, String> customerIdExtractor, ServerWebExchange exchange) {
    return resourceMono
      .flatMap(resource -> {
        String resourceCustomerId = customerIdExtractor.apply(resource);
        return validateReadAccess(resourceCustomerId, exchange)
          .thenReturn(resource);
      });
  }
}

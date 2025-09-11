package com.bootcamp.ntt.card_service.client;

import com.bootcamp.ntt.card_service.client.dto.account.*;
import com.bootcamp.ntt.card_service.exception.AccountNotFoundException;
import com.bootcamp.ntt.card_service.exception.AccountServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountServiceClient {

  private final WebClient webClient;

  @Value("${services.account.base-url:http://localhost:8081}")
  private String accountServiceUrl;

  public Mono<AccountBalanceResponse> getAccountBalance(String accountId) {
    log.debug("Fetching account balance for ID: {}", accountId);

    return webClient
      .get()
      .uri(accountServiceUrl + "/accounts/{id}/balance", accountId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("Account not found: {}", accountId);
        return Mono.error(new AccountNotFoundException("Account not found: " + accountId));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Account service error for account: {}", accountId);
        return Mono.error(new AccountServiceException("Error communicating with account service"));
      })
      .bodyToMono(AccountBalanceResponse.class)
      .doOnSuccess(response -> log.debug("Account balance retrieved for ID: {}", accountId))
      .doOnError(error -> log.error("Error fetching account balance: {}", error.getMessage()));
  }

  public Mono<AccountDetailsResponse> getAccountDetails(String accountId) {
    log.debug("Fetching account details for ID: {}", accountId);

    return webClient
      .get()
      .uri(accountServiceUrl + "/accounts/{id}/details", accountId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("Account not found: {}", accountId);
        return Mono.error(new AccountNotFoundException("Account not found: " + accountId));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Account service error for account: {}", accountId);
        return Mono.error(new AccountServiceException("Error communicating with account service"));
      })
      .bodyToMono(AccountDetailsResponse.class)
      .doOnSuccess(response -> log.debug("Account details retrieved for ID: {}", accountId))
      .doOnError(error -> log.error("Error fetching account details: {}", error.getMessage()));
  }

  /**
   * Valida que una cuenta pertenezca a un cliente específico
   */
  public Mono<Boolean> validateAccountOwnership(String accountId, String customerId) {
    log.debug("Validating account {} ownership for customer {}", accountId, customerId);

    return webClient
      .get()
      .uri(accountServiceUrl + "/accounts/{accountId}/validate-owner/{customerId}", accountId, customerId)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("Account {} does not belong to customer {}", accountId, customerId);
        return Mono.error(new AccountServiceException("Account does not belong to customer"));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Account service error validating ownership");
        return Mono.error(new RuntimeException("Error validating account ownership"));
      })
      .bodyToMono(Boolean.class)
      .onErrorReturn(false) // Si hay error, asumir que no es owner
      .doOnSuccess(isOwner -> log.debug("Account {} ownership validation for customer {}: {}",
        accountId, customerId, isOwner));
  }

  public Mono<AccountTransactionResponse> debitAccount(String accountId, AccountDebitRequest request) {
    log.debug("Debiting account: {} with amount: {}", accountId, request.getAmount());

    return webClient
      .post()
      .uri(accountServiceUrl + "/accounts/{id}/debit", accountId)
      .bodyValue(request)
      .retrieve()
      .onStatus(HttpStatus::is4xxClientError, response -> {
        log.warn("Invalid debit request for account: {}", accountId);
        return Mono.error(new AccountServiceException("Invalid debit request for account: " + accountId));
      })
      .onStatus(HttpStatus::is5xxServerError, response -> {
        log.error("Account service error during debit for account: {}", accountId);
        return Mono.error(new AccountServiceException("Error processing debit with account service"));
      })
      .bodyToMono(AccountTransactionResponse.class)
      .doOnSuccess(response -> log.debug("Account debited successfully: {}", accountId))
      .doOnError(error -> log.error("Error debiting account: {}", error.getMessage()));
  }
}

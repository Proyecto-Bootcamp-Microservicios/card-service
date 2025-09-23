package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.AccountServiceClient;
import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.account.AccountCreditRequest;
import com.bootcamp.ntt.card_service.client.dto.account.AccountDebitRequest;
import com.bootcamp.ntt.card_service.client.dto.account.AccountTransactionResponse;
import com.bootcamp.ntt.card_service.client.dto.transaction.AccountUsage;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.entity.DebitCard;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.exception.CardServiceException;
import com.bootcamp.ntt.card_service.exception.EntityNotFoundException;
import com.bootcamp.ntt.card_service.service.ExternalServiceWrapper;
import com.bootcamp.ntt.card_service.utils.ErrorCodes;
import com.bootcamp.ntt.card_service.mapper.DebitCardMapper;
import com.bootcamp.ntt.card_service.model.DebitCardResponse;
import com.bootcamp.ntt.card_service.model.DebitCardCreateRequest;
import com.bootcamp.ntt.card_service.model.DebitCardUpdateRequest;
import com.bootcamp.ntt.card_service.model.DebitPurchaseRequest;
import com.bootcamp.ntt.card_service.model.DebitPurchaseResponse;
import com.bootcamp.ntt.card_service.model.PrimaryAccountBalanceResponse;
import com.bootcamp.ntt.card_service.model.AssociateAccountRequest;
import com.bootcamp.ntt.card_service.repository.DebitCardRepository;
import com.bootcamp.ntt.card_service.service.DebitCardService;
import com.bootcamp.ntt.card_service.utils.CardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.bootcamp.ntt.card_service.utils.CacheKeys.MASTER_DATA_TTL;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebitCardServiceImpl implements DebitCardService {

  private final DebitCardRepository debitCardRepository;
  private final AccountServiceClient accountServiceClient;
  private final TransactionServiceClient transactionServiceClient;
  private final DebitCardMapper debitCardMapper;
  private final CardUtils cardUtils;
  private final ExternalServiceWrapper externalServiceWrapper;
  private final ReactiveRedisTemplate<String, Object> redisTemplate;

  @Override
  public Flux<DebitCardResponse> getDebitCardsByActive(Boolean isActive) {
    return debitCardRepository.findByIsActiveAndType(isActive, CardType.DEBIT)
      .map(debitCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Active cards retrieved"));
  }

  @Override
  public Flux<DebitCardResponse> getDebitCardsByActiveAndCustomer(Boolean isActive, String customerId) {
    return debitCardRepository.findByIsActiveAndCustomerIdAndType(isActive, customerId, CardType.DEBIT)
      .map(debitCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards active by customer retrieved"));
  }

  @Override
  public Mono<DebitCardResponse> getCardById(String id) {
    String cacheKey = "debit:master:id:" + id;
    log.debug("Getting debit card by ID: {}", id);

    return getCachedValue(cacheKey, DebitCardResponse.class)
      .switchIfEmpty(
        debitCardRepository.findById(id)
          .map(debitCardMapper::toResponse)
          .flatMap(response ->
            setCachedValue(cacheKey, response, MASTER_DATA_TTL)
              .thenReturn(response)
          )
          .doOnSuccess(card -> {
            if (card != null) {
              log.debug("Debit card found and cached: {}", id);
            } else {
              log.debug("Debit card not found: {}", id);
            }
          })
      );
  }

  @Override
  public Mono<DebitCardResponse> getDebitCardByCardNumber(String cardNumber) {
    String cacheKey = "debit:master:number:" + cardNumber;
    log.debug("Getting debit card by cardNumber: {}", cardNumber);

    return getCachedValue(cacheKey, DebitCardResponse.class)
      .switchIfEmpty(
        debitCardRepository.findByCardNumber(cardNumber)
          .map(debitCardMapper::toResponse)
          .flatMap(response ->
            setCachedValue(cacheKey, response, MASTER_DATA_TTL)
              .thenReturn(response)
          )
          .doOnSuccess(card -> {
            if (card != null) {
              log.debug("Debit card found and cached: {}", cardNumber);
            } else {
              log.debug("Debit card not found: {}", cardNumber);
            }
          })
      );
  }

  @Override
  public Mono<DebitCardResponse> createCard(DebitCardCreateRequest cardRequest) {
    log.debug("Creating debit card for customer: {}", cardRequest.getCustomerId());

    return generateUniqueDebitCardNumber()
      .map(cardNumber -> debitCardMapper.toEntity(cardRequest, cardNumber))
      .flatMap(debitCardRepository::save)
      .map(debitCardMapper::toResponse)
      .doOnSuccess(response -> log.debug("Debit card created with ID: {}", response.getId()));
  }

  @Override
  public Mono<DebitCardResponse> updateCard(String id, DebitCardUpdateRequest cardRequest) {
    log.debug("Updating debit card with ID: {}", id);

    return debitCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Debit card not found")))
      .map(existing -> debitCardMapper.updateEntity(existing, cardRequest))
      .flatMap(debitCardRepository::save)
      .map(card -> {
        invalidateDebitCardCaches(card.getId(), card.getCardNumber());
        return debitCardMapper.toResponse(card);
      })
      .doOnSuccess(response -> log.debug("Debit card updated with ID: {}", response.getId()));
  }

  @Override
  public Mono<Void> deleteCard(String id) {
    return debitCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Debit card not found")))
      .flatMap(card -> {
        String cardNumber = card.getCardNumber();

        return debitCardRepository.delete(card)
          .doOnSuccess(unused -> {
            log.debug("Debit card deleted");
            invalidateDebitCardCaches(id, cardNumber);
          });
      });
  }

  @Override
  public Mono<DebitCardResponse> deactivateCard(String id) {
    return debitCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(false);  // soft delete
        return debitCardRepository.save(card);
      })
      .map(debitCardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Card {} deactivated", id));
  }

  @Override
  public Mono<DebitCardResponse> activateCard(String id) {
    return debitCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(true);  // reactivar
        return debitCardRepository.save(card);
      })
      .map(debitCardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Card {} activated", id));
  }

  //helpers
  private <T> Mono<T> getCachedValue(String key, Class<T> valueType) {
    return redisTemplate.opsForValue()
      .get(key)
      .cast(valueType)
      .doOnNext(cached -> log.debug("REDIS CACHE HIT: {}", key))
      .onErrorResume(error -> {
        log.warn("Redis read error for key {}: {}", key, error.getMessage());
        return Mono.empty();
      });
  }

  private Mono<Boolean> setCachedValue(String key, Object value, Duration ttl) {
    return redisTemplate.opsForValue()
      .set(key, value, ttl)
      .doOnSuccess(success -> {
        if (success) {
          log.debug("REDIS CACHE SET: {} (TTL: {})", key, ttl);
        } else {
          log.warn("Redis cache SET failed for key: {}", key);
        }
      })
      .onErrorResume(error -> {
        log.error("Redis write error for key {}: {}", key, error.getMessage());
        return Mono.just(false);
      });
  }

  //invalidacion
  private void invalidateDebitCardCaches(String cardId, String cardNumber) {
    Flux.just(
        "debit:master:id:" + cardId,
        "debit:master:number:" + cardNumber
      )
      .flatMap(redisTemplate::delete)
      .doOnNext(deleted -> log.debug("Debit card cache invalidated: {}", deleted))
      .subscribe();
  }

  @Override
  public Mono<DebitCardResponse> associateAccountToDebitCard(String debitCardId, AssociateAccountRequest request) {
    log.debug("Associating account {} to debit card {}", request.getAccountId(), debitCardId);

    return debitCardRepository.findById(debitCardId)
      .switchIfEmpty(Mono.error(new CardServiceException(
        "Debit card not found with id: " + debitCardId,
        ErrorCodes.CARD_NOT_FOUND,
        HttpStatus.NOT_FOUND
      )))
      .flatMap(debitCard -> validateAndAssociateAccount(debitCard, request.getAccountId()))
      .flatMap(debitCardRepository::save)
      .map(debitCardMapper::toResponse)
      .doOnSuccess(response -> log.info("Account {} associated successfully to debit card {}",
        request.getAccountId(), debitCardId));
  }

  private Mono<DebitCard> validateAndAssociateAccount(DebitCard debitCard, String accountId) {
    // 1. Validar que la tarjeta esté activa
    if (!debitCard.isActive()) {
      return Mono.error(new CardServiceException(
        "Cannot associate account to inactive debit card",
        ErrorCodes.CARD_INACTIVE,
        HttpStatus.BAD_REQUEST
      ));
    }

    // 2. Validar que la cuenta no esté ya asociada
    if (debitCard.getAssociatedAccountIds() != null &&
      debitCard.getAssociatedAccountIds().contains(accountId)) {
      return Mono.error(new CardServiceException(
        "Account is already associated to this debit card",
        ErrorCodes.ACCOUNT_ALREADY_ASSOCIATED,
        HttpStatus.BAD_REQUEST
      ));
    }

    // 3. Validar que la cuenta pertenezca al mismo cliente (llamada al account-service)
    /*return accountServiceClient.validateAccountOwnership(accountId, debitCard.getCustomerId())
        .flatMap(isOwner -> {
            if (!isOwner) {
                return Mono.error(new CardServiceException(
                    "Account does not belong to the card owner",
                    ErrorCodes.INVALID_ACCOUNT_OWNERSHIP,
                    HttpStatus.BAD_REQUEST
                ));
            }
            return Mono.just(debitCard);
        })
        .map(card -> processAccountAssociation(card, accountId));*/
    return Mono.just(true)
      .flatMap(isOwner -> {
        if (!isOwner) {
          return Mono.error(new CardServiceException(
            "Account does not belong to the card owner",
            ErrorCodes.INVALID_ACCOUNT_OWNERSHIP,
            HttpStatus.BAD_REQUEST
          ));
        }
        return Mono.just(debitCard);
      })
      .map(card -> processAccountAssociation(card, accountId));
  }

  private DebitCard processAccountAssociation(DebitCard debitCard, String accountId) {
    // Si no tiene cuenta principal, esta será la principal
    if (debitCard.getPrimaryAccountId() == null || debitCard.getPrimaryAccountId().isEmpty()) {
      debitCard.setPrimaryAccountId(accountId);
      log.debug("Setting account {} as primary for debit card {}", accountId, debitCard.getId());
    }

    // Agregar a la lista de cuentas asociadas
    if (debitCard.getAssociatedAccountIds() == null || debitCard.getAssociatedAccountIds().isEmpty()) {
      debitCard.setAssociatedAccountIds(new ArrayList<>());
    }

    debitCard.getAssociatedAccountIds().add(accountId);

    return debitCard;
  }

  @Override
  public Mono<DebitPurchaseResponse> processDebitCardPurchase(String cardNumber, DebitPurchaseRequest request) {
    log.debug("Processing debit card purchase for card: {}", cardNumber);

    return debitCardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new EntityNotFoundException("Debit card not found: " + cardNumber)))
      .flatMap(debitCard -> validateDebitCard(debitCard)
        .then(validateAmount(request.getAmount()))
        .then(processCascadePayment(debitCard, request.getAmount()))
        .flatMap(accountsUsed -> createDebitPurchaseTransactionInTransactionService(debitCard, request, accountsUsed)
          .map(transaction -> debitCardMapper.toDebitPurchaseResponse(debitCard, request, accountsUsed, transaction)))
      )
      .doOnSuccess(response -> log.debug("Purchase processed successfully for card: {}", cardNumber));
  }

  private Mono<Void> validateDebitCard(DebitCard debitCard) {
    if (!debitCard.isActive()) {
      return Mono.error(new BusinessRuleException("Debit card is inactive", "CARD_INACTIVE"));
    }
    return Mono.empty();
  }

  private Mono<Void> validateAmount(Double amount) {
    if (amount == null || amount <= 0) {
      return Mono.error(new BusinessRuleException("Invalid amount", "INVALID_AMOUNT"));
    }
    return Mono.empty();
  }

  private Mono<List<AccountUsage>> processCascadePayment(DebitCard debitCard, Double amount) {
    return getOrderedAccounts(debitCard)
      .flatMapMany(Flux::fromIterable)
      .concatMap(accountId -> processAccountPayment(accountId, amount))
      .takeUntil(accountUsage -> accountUsage.getRemainingBalance() <= 0)
      .collectList()
      .flatMap(accountsUsed -> {
        double totalProcessed = accountsUsed.stream()
          .mapToDouble(AccountUsage::getAmountDeducted)
          .sum();

        if (totalProcessed < amount) {
          return Mono.error(new BusinessRuleException("Insufficient funds", "INSUFFICIENT_FUNDS"));
        }
        return Mono.just(accountsUsed);
      });
  }

  private Mono<AccountUsage> processAccountPayment(String accountId, Double remainingAmount) {
    return externalServiceWrapper.getAccountBalanceWithCircuitBreaker(accountId)
      .flatMap(accountBalance -> {
        if (accountBalance.getAvailableBalance() > 0) {
          Double amountToDeduct = Math.min(remainingAmount, accountBalance.getAvailableBalance());

          return externalServiceWrapper.debitAccountWithCircuitBreaker(
            accountId,
            new AccountDebitRequest(amountToDeduct, "DEBIT_CARD_PAYMENT", UUID.randomUUID().toString())
          ).map(transactionResponse -> {
            AccountUsage accountUsage = new AccountUsage();
            accountUsage.setAccountId(accountId);
            accountUsage.setAmountDeducted(amountToDeduct);
            accountUsage.setRemainingBalance(accountBalance.getAvailableBalance() - amountToDeduct);
            return accountUsage;
          });
        }

        // Cuando no hay saldo disponible
        AccountUsage accountUsage = new AccountUsage();
        accountUsage.setAccountId(accountId);
        accountUsage.setAmountDeducted(0.0);
        accountUsage.setRemainingBalance(accountBalance.getAvailableBalance());
        return Mono.just(accountUsage);
      });
  }

  private Mono<List<String>> getOrderedAccounts(DebitCard debitCard) {
    List<String> orderedAccounts = new ArrayList<>();

    // Cuenta principal primero
    orderedAccounts.add(debitCard.getPrimaryAccountId());

    // Cuentas asociadas (si existen)
    if (debitCard.getAssociatedAccountIds() != null && !debitCard.getAssociatedAccountIds().isEmpty()) {
      orderedAccounts.addAll(debitCard.getAssociatedAccountIds());
    }

    return Mono.just(orderedAccounts);
  }

  private Mono<String> createDebitPurchaseTransactionInTransactionService(DebitCard debitCard,
                                                                          DebitPurchaseRequest request,
                                                                          List<AccountUsage> accountsUsed) {
    TransactionRequest transactionRequest = debitCardMapper.toTransactionRequest(
      debitCard, request, accountsUsed, cardUtils.generateAuthCode());

    return externalServiceWrapper.createDebitCardPurchaseTransactionWithCircuitBreaker(transactionRequest)
      .then(Mono.just(generateTransactionId()))
      .onErrorResume(error -> {
        log.error("CRITICAL: Debit purchase transaction service failed after successful purchase: {}", error.getMessage());

        // Rollback de los débitos en las cuentas
        return revertAccountDebits(accountsUsed)
          .then(Mono.error(new RuntimeException(
            "Debit purchase transaction could not be recorded. Payment has been reverted. Please try again.")));
      });
  }

  private Mono<Void> revertAccountDebits(List<AccountUsage> accountsUsed) {
    log.warn("Initiating debit reversion for {} accounts", accountsUsed.size());

    return Flux.fromIterable(accountsUsed)
      .filter(accountUsage -> accountUsage.getAmountDeducted() > 0)
      .flatMap(this::revertSingleAccountDebit)
      .then()
      .doOnSuccess(ignored -> log.info("Debit reversion process completed successfully"))
      .doOnError(error -> log.error("Debit reversion process failed: {}", error.getMessage()));
  }

  private Mono<AccountTransactionResponse> revertSingleAccountDebit(AccountUsage accountUsage) {
    String transactionId = UUID.randomUUID().toString();

    AccountCreditRequest creditRequest = new AccountCreditRequest(
      BigDecimal.valueOf(accountUsage.getAmountDeducted()),
      "REVERT_DEBIT_CARD_PURCHASE"
    );

    return externalServiceWrapper.creditAccountWithCircuitBreaker(accountUsage.getAccountId(), creditRequest)
      .doOnSuccess(response -> log.info("Reverted {} for account {} (txn: {})",
        accountUsage.getAmountDeducted(), accountUsage.getAccountId(), transactionId))
      .onErrorResume(error -> {
        log.error("FAILED to revert {} for account {} (txn: {}): {}",
          accountUsage.getAmountDeducted(), accountUsage.getAccountId(), transactionId, error.getMessage());
        return Mono.empty(); // Continuar con otras reversiones
      });
  }


  private String generateTransactionId() {
    return "DTX-" + System.currentTimeMillis();
  }

  @Override
  public Mono<PrimaryAccountBalanceResponse> getDebitCardPrimaryAccountBalance(String cardId) {
    log.debug("Getting primary account balance for debit card: {}", cardId);

    return debitCardRepository.findById(cardId)
      .switchIfEmpty(Mono.error(new CardServiceException(
        "DEBIT_CARD_NOT_FOUND",
        "Debit card not found: " + cardId,
        HttpStatus.NOT_FOUND)))
      .flatMap(debitCard -> {
        String primaryAccountId = debitCard.getPrimaryAccountId();

        return externalServiceWrapper.getAccountBalanceWithCircuitBreaker(primaryAccountId)
          .map(accountBalance -> debitCardMapper.toPrimaryAccountBalanceResponse(
            cardId, debitCard, accountBalance));
      })
      .doOnSuccess(response -> log.debug("Primary account balance retrieved for card: {}", cardId));
  }

  @Override
  public Mono<Integer> getActiveCardsCount() {
    log.debug("Getting total count of active debit cards");

    return debitCardRepository.countByIsActiveAndType(true, CardType.DEBIT)
      .map(Long::intValue)
      .doOnSuccess(count -> log.debug("Found {} active debit cards", count));
  }

  private Mono<String> generateUniqueDebitCardNumber() {
    String candidate = cardUtils.generateRandomCardNumber();
    return debitCardRepository.findByCardNumber(candidate)
      .flatMap(existing -> generateUniqueDebitCardNumber())
      .switchIfEmpty(Mono.just(candidate));
  }

}

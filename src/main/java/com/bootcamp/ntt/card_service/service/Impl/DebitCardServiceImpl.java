package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.AccountServiceClient;
import com.bootcamp.ntt.card_service.client.CustomerServiceClient;
import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.account.*;
import com.bootcamp.ntt.card_service.client.dto.transaction.*;
import com.bootcamp.ntt.card_service.entity.DebitCard;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.exception.*;
import com.bootcamp.ntt.card_service.mapper.CreditCardMapper;
import com.bootcamp.ntt.card_service.mapper.DebitCardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.repository.DebitCardRepository;
import com.bootcamp.ntt.card_service.service.DebitCardService;
import com.bootcamp.ntt.card_service.service.CreditCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebitCardServiceImpl implements DebitCardService {

  private final DebitCardRepository debitCardRepository;
  private final AccountServiceClient accountServiceClient;
  private final CustomerServiceClient customerServiceClient;
  private final TransactionServiceClient transactionServiceClient;
  private final CreditCardService creditCardService;
  private final CreditCardMapper creditCardMapper;
  private final DebitCardMapper debitCardMapper;

  @Override
  public Flux<DebitCardResponse> getAllDebitCards(Boolean isActive) {
    return debitCardRepository.findAll()
      .map(debitCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards retrieved"));
  }

  @Override
  public Flux<DebitCardResponse> getDebitCardsByActive(Boolean isActive) {
    return debitCardRepository.findByIsActiveAndType(isActive, CardType.DEBIT)
      .map(debitCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Active cards retrieved"));
  }

  @Override
  public Flux<DebitCardResponse> getDebitCardsByActiveAndCustomer(Boolean isActive, String customerId) {
    return debitCardRepository.findByIsActiveAndCustomerIdAndType(isActive, customerId,CardType.DEBIT)
      .map(debitCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards active by customer retrieved"));
  }

  @Override
  public Mono<DebitCardResponse> getCardById(String id) {
    log.debug("Getting credit card by ID: {}", id);
    return debitCardRepository.findById(id)
      .map(debitCardMapper::toResponse)
      .doOnSuccess(credit -> {
        if (credit != null) {
          log.debug("Card found with ID: {}", id);
        } else {
          log.debug("Card not found with ID: {}", id);
        }
      });
  }

  @Override
  public Mono<DebitCardResponse> createCard(DebitCardCreateRequest cardRequest) {
    log.debug("Creating debit card for customer: {}", cardRequest.getCustomerId());

    return generateUniqueDebitCardNumber()
      .map(cardNumber -> debitCardMapper.toEntity(cardRequest, cardNumber))
      .flatMap(debitCardRepository::save)
      .map(debitCardMapper::toResponse)
      .doOnSuccess(response -> log.debug("Debit card created with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error creating debit card: {}", error.getMessage()));
  }

  @Override
  public Mono<DebitCardResponse> updateCard(String id, DebitCardUpdateRequest cardRequest) {
    log.debug("Updating card with ID: {}", id);

    return debitCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Debit card not found")))
      .map(existing -> debitCardMapper.updateEntity(existing, cardRequest))
      .flatMap(debitCardRepository::save)
      .map(debitCardMapper::toResponse)
      .doOnSuccess(response -> log.debug("Card updated with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error updating card {}: {}", id, error.getMessage()));
  }

  @Override
  public Mono<Void> deleteCard(String id) {
    return debitCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Debit card not found")))
      .flatMap(debitCardRepository::delete)
      .doOnSuccess(unused -> log.debug("Card deleted"))
      .doOnError(error -> log.error("Error deleting card {}: {}", id, error.getMessage()));
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
      .doOnSuccess(c -> log.debug("Card {} deactivated", id))
      .doOnError(e -> log.error("Error deactivating card {}: {}", id, e.getMessage()));
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
      .doOnSuccess(c -> log.debug("Card {} activated", id))
      .doOnError(e -> log.error("Error activating card {}: {}", id, e.getMessage()));
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
    return Mono.just(false)
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
        .flatMap(accountsUsed -> createTransactionInTransactionService(debitCard, request, accountsUsed)
          .map(transaction -> buildDebitPurchaseResponse(debitCard, request, accountsUsed, transaction)))
      )
      .doOnSuccess(response -> log.debug("Purchase processed successfully for card: {}", cardNumber))
      .doOnError(error -> log.error("Error processing purchase for card {}: {}", cardNumber, error.getMessage()));
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
    return accountServiceClient.getAccountBalance(accountId)
      .flatMap(accountBalance -> {
        if (accountBalance.getAvailableBalance() > 0) {
          Double amountToDeduct = Math.min(remainingAmount, accountBalance.getAvailableBalance());

          return accountServiceClient.debitAccount(
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
      })
      .onErrorResume(e -> {
        log.warn("Error processing account {}: {}", accountId, e.getMessage());
        AccountUsage accountUsage = new AccountUsage();
        accountUsage.setAccountId(accountId);
        accountUsage.setAmountDeducted(0.0);
        accountUsage.setRemainingBalance(0.0);
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

  private Mono<String> createTransactionInTransactionService(DebitCard debitCard,
                                                             DebitPurchaseRequest request,
                                                             List<AccountUsage> accountsUsed) {
    TransactionCreateRequest transactionRequest = new TransactionCreateRequest();
    transactionRequest.setCardId(debitCard.getId());
    transactionRequest.setAmount(request.getAmount());
    transactionRequest.setTransactionType(mapTransactionType(request.getTransactionType()));
    transactionRequest.setStatus("APPROVED");
    transactionRequest.setTimestamp(OffsetDateTime.now());

    // Para MVP, podemos agregar info básica en descripción
    /*String description = String.format("%s - %s accounts used",
      request.getTransactionType(), accountsUsed.size());
    transactionRequest.setDescription(description);*/

    return transactionServiceClient.createTransaction(transactionRequest)
      .then(Mono.just(generateTransactionId())); // Devolver un ID para la respuesta
  }

  private String mapTransactionType(DebitPurchaseRequest.TransactionTypeEnum transactionType) {
    switch (transactionType) {
      case PURCHASE: return "DEBIT_PURCHASE";
      case WITHDRAWAL: return "DEBIT_WITHDRAWAL";
      case PAYMENT: return "DEBIT_PAYMENT";
      default: return "DEBIT_TRANSACTION";
    }
  }

  private String generateTransactionId() {
    return "DTX-" + System.currentTimeMillis();
  }

  private DebitPurchaseResponse buildDebitPurchaseResponse(DebitCard debitCard,
                                                           DebitPurchaseRequest request,
                                                           List<AccountUsage> accountsUsed,
                                                           String transactionId) {
    double totalProcessed = accountsUsed.stream()
      .mapToDouble(AccountUsage::getAmountDeducted)
      .sum();

    DebitPurchaseResponse response = new DebitPurchaseResponse();
    response.setSuccess(true);
    response.setTransactionId(transactionId);
    response.setCardId(debitCard.getId());
    response.setRequestedAmount(request.getAmount());
    response.setProcessedAmount(totalProcessed);
    response.setProcessedAt(OffsetDateTime.now());

    // Mapear accounts used
    List<DebitPurchaseResponseAccountsUsedInner> accountsUsedResponse = accountsUsed.stream()
      .map(usage -> {
        DebitPurchaseResponseAccountsUsedInner accountUsed = new DebitPurchaseResponseAccountsUsedInner();
        accountUsed.setAccountId(usage.getAccountId());
        accountUsed.setAmountDeducted(usage.getAmountDeducted());
        accountUsed.setRemainingBalance(usage.getRemainingBalance());
        return accountUsed;
      })
      .collect(Collectors.toList());

    response.setAccountsUsed(accountsUsedResponse);

    return response;
  }

  @Override
  public Mono<PrimaryAccountBalanceResponse> getDebitCardPrimaryAccountBalance(String cardId) {
    log.debug("Getting primary account balance for debit card: {}", cardId);

    return debitCardRepository.findById(cardId)
      .switchIfEmpty(Mono.error(new EntityNotFoundException("Debit card not found: " + cardId)))
      .flatMap(debitCard -> Mono.zip(
        accountServiceClient.getAccountBalance(debitCard.getPrimaryAccountId()),
        accountServiceClient.getAccountDetails(debitCard.getPrimaryAccountId())
      ).map(tuple -> {
        AccountBalanceResponse accountBalance = tuple.getT1();
        AccountDetailsResponse accountDetails = tuple.getT2();

        PrimaryAccountBalanceResponse response = new PrimaryAccountBalanceResponse();
        response.setCardId(cardId);
        response.setPrimaryAccountId(debitCard.getPrimaryAccountId());
        response.setAccountNumber(accountDetails.getAccountNumber());
        response.setBalance(accountBalance.getAvailableBalance());
        response.setCurrency(accountDetails.getCurrency());
        response.setAccountType(PrimaryAccountBalanceResponse.AccountTypeEnum.valueOf(accountDetails.getAccountType()));
        return response;
      }))
      .doOnSuccess(response -> log.debug("Primary account balance retrieved for card: {}", cardId))
      .doOnError(error -> log.error("Error getting primary account balance for card {}: {}", cardId, error.getMessage()));
  }

  public Mono<CardMovementsResponse> getCardMovements(String cardId, Integer limit) {
    Integer actualLimit = (limit != null && limit <= 50) ? limit : 10;
    log.debug("Getting last {} movements for card: {}", actualLimit, cardId);

    return determineCardTypeReactive(cardId)
      .flatMap(cardType ->
        transactionServiceClient.getLastCardMovements(cardId, actualLimit)
          .collectList()
          .map(transactions -> buildCardMovementsResponse(cardId, cardType, transactions))
      )
      .doOnSuccess(response -> log.debug("Movements retrieved for card: {}", cardId));
  }

  private Mono<String> determineCardTypeReactive(String cardId) {
    return debitCardRepository.findById(cardId)
      .map(debitCard -> "DEBIT")
      .switchIfEmpty(
        creditCardService.getCardById(cardId)
          .map(creditCard -> "CREDIT")
          .switchIfEmpty(Mono.error(new CardServiceException(
            "CARD_NOT_FOUND",
            "Card not found: " + cardId,
            HttpStatus.NOT_FOUND)))
      );
  }

  private CardMovementsResponse buildCardMovementsResponse(
    String cardId,
    String cardType,
    List<TransactionResponse> transactions) {

    CardMovementsResponse response = new CardMovementsResponse();
    response.setCardId(cardId);
    response.setCardType(CardMovementsResponse.CardTypeEnum.valueOf(cardType));
    response.setMovements(mapToCardMovements(transactions));
    response.setTotalCount(transactions.size());
    response.setRetrievedAt(OffsetDateTime.now());
    return response;
  }

  private List<CardMovement> mapToCardMovements(List<TransactionResponse> transactions) {
    return transactions.stream()
      .map(this::mapTransactionToCardMovement)
      .collect(Collectors.toList());
  }

  private CardMovement mapTransactionToCardMovement(TransactionResponse transaction) {
    CardMovement movement = new CardMovement();
    movement.setTransactionId(transaction.getTransactionId());
    movement.setAmount(transaction.getAmount());

    try {
      movement.setTransactionType(
        CardMovement.TransactionTypeEnum.valueOf(transaction.getTransactionType())
      );
      movement.setStatus(
        CardMovement.StatusEnum.valueOf(transaction.getStatus())
      );
    } catch (IllegalArgumentException e) {
      log.warn("Unknown enum value in transaction {}: {}", transaction.getTransactionId(), e.getMessage());
    }

    movement.setMerchantInfo(transaction.getMerchantInfo());
    movement.setProcessedAt(transaction.getProcessedAt());

    if (transaction.getAccountsAffected() != null) {
      movement.setAccountsAffected(mapAccountsAffected(transaction.getAccountsAffected()));
    }

    return movement;
  }

  private List<CardMovementAccountsAffectedInner> mapAccountsAffected(
    List<TransactionAccount> accountsAffected) {

    return accountsAffected.stream()
      .map(account -> {
        CardMovementAccountsAffectedInner affected = new CardMovementAccountsAffectedInner();
        affected.setAccountId(account.getAccountId());
        affected.setAmountDeducted(account.getAmountDeducted());
        return affected;
      })
      .collect(Collectors.toList());
  }



  @Override
  public Mono<Integer> getActiveCardsCount() {
    log.debug("Getting total count of active debit cards");

    return debitCardRepository.countByIsActiveAndType(true, CardType.DEBIT)
      .map(Long::intValue)
      .doOnSuccess(count -> log.debug("Found {} active debit cards", count))
      .doOnError(error -> log.error("Error counting active debit cards: {}", error.getMessage()));
  }


  private Mono<String> generateUniqueDebitCardNumber() {
    String candidate = creditCardService.generateRandomCardNumber();

    return debitCardRepository.findByCardNumber(candidate)
      .flatMap(existing -> generateUniqueDebitCardNumber()) // si existe, intenta de nuevo
      .switchIfEmpty(Mono.just(candidate)); // si no existe, úsalo
  }



}

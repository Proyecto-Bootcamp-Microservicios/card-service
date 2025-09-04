package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.service.DebitCardService;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.exception.CardNotFoundException;
import com.bootcamp.ntt.card_service.exception.CustomerNotFoundException;
import com.bootcamp.ntt.card_service.exception.InsufficientFundsException;
import com.bootcamp.ntt.card_service.exception.InvalidAmountException;
import com.bootcamp.ntt.card_service.repository.DebitCardRepository;
import com.bootcamp.ntt.card_service.repository.CreditCardRepository;
import com.bootcamp.ntt.card_service.service.DebitService;
import com.bootcamp.ntt.card_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class DebitCardServiceImpl implements DebitCardService {
  private final DebitCardRepository debitCardRepository;
  private final CreditCardRepository creditCardRepository;
  private final CardMovementRepository cardMovementRepository;
  private final AccountService accountService;

  @Override
  public Mono<DebitPurchaseResponse> processDebitCardPurchase(String cardNumber, DebitPurchaseRequest request) {
    log.info("Processing debit card purchase for card: {} amount: {}", cardNumber, request.getAmount());

    // Validate amount
    if (request.getAmount() <= 0) {
      return Mono.error(new InvalidAmountException("Amount must be greater than 0"));
    }

    return debitCardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new CardNotFoundException("Debit card not found: " + cardNumber)))
      .filter(DebitCard::getIsActive)
      .switchIfEmpty(Mono.error(new CardNotFoundException("Debit card is inactive")))
      .flatMap(debitCard -> processPaymentWithCascade(debitCard, request));
  }

  private Mono<DebitPurchaseResponse> processPaymentWithCascade(DebitCard debitCard, DebitPurchaseRequest request) {
    String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    List<AccountUsed> accountsUsed = new ArrayList<>();
    double remainingAmount = request.getAmount();

    // First try primary account
    return accountService.getAccountBalance(debitCard.getPrimaryAccountId())
      .flatMap(primaryBalance -> {
        if (primaryBalance >= remainingAmount) {
          // Primary account has sufficient funds
          return accountService.deductFromAccount(debitCard.getPrimaryAccountId(), remainingAmount)
            .map(newBalance -> {
              AccountUsed accountUsed = new AccountUsed();
              accountUsed.setAccountId(debitCard.getPrimaryAccountId());
              accountUsed.setAmountDeducted(remainingAmount);
              accountUsed.setRemainingBalance(newBalance);
              accountsUsed.add(accountUsed);
              return accountsUsed;
            });
        } else {
          // Need to use cascade logic with associated accounts
          return processCascadePayment(debitCard, remainingAmount, accountsUsed);
        }
      })
      .flatMap(usedAccounts -> {
        if (usedAccounts.stream().mapToDouble(AccountUsed::getAmountDeducted).sum() < request.getAmount()) {
          return Mono.error(new InsufficientFundsException("Insufficient funds across all associated accounts"));
        }

        // Save transaction record
        return saveCardMovement(debitCard.getCardId(), request, transactionId, "COMPLETED")
          .then(Mono.fromCallable(() -> buildSuccessResponse(debitCard, request, transactionId, usedAccounts)));
      })
      .onErrorResume(throwable -> {
        // Save failed transaction
        return saveCardMovement(debitCard.getCardId(), request, transactionId, "FAILED")
          .then(buildErrorResponse(throwable));
      });
  }

  private Mono<List<AccountUsed>> processCascadePayment(DebitCard debitCard, double remainingAmount, List<AccountUsed> accountsUsed) {
    // This would implement the cascade logic across associated accounts
    // For brevity, returning a simplified implementation
    return Flux.fromIterable(debitCard.getAssociatedAccountIds())
      .concatMap(accountId ->
        accountService.getAccountBalance(accountId)
          .flatMap(balance -> {
            double toDeduct = Math.min(balance, remainingAmount);
            if (toDeduct > 0) {
              return accountService.deductFromAccount(accountId, toDeduct)
                .map(newBalance -> {
                  AccountUsed accountUsed = new AccountUsed();
                  accountUsed.setAccountId(accountId);
                  accountUsed.setAmountDeducted(toDeduct);
                  accountUsed.setRemainingBalance(newBalance);
                  return accountUsed;
                });
            }
            return Mono.empty();
          })
      )
      .collectList()
      .map(accounts -> {
        accountsUsed.addAll(accounts);
        return accountsUsed;
      });
  }

  @Override
  public Mono<PrimaryAccountBalanceResponse> getDebitCardPrimaryAccountBalance(String cardId) {
    log.info("Getting primary account balance for debit card: {}", cardId);

    return debitCardRepository.findById(cardId)
      .switchIfEmpty(Mono.error(new CardNotFoundException("Debit card not found: " + cardId)))
      .flatMap(debitCard ->
        accountService.getAccountDetails(debitCard.getPrimaryAccountId())
          .map(accountDetails -> {
            PrimaryAccountBalanceResponse response = new PrimaryAccountBalanceResponse();
            response.setCardId(cardId);
            response.setPrimaryAccountId(debitCard.getPrimaryAccountId());
            response.setAccountNumber(accountDetails.getAccountNumber());
            response.setBalance(accountDetails.getBalance());
            response.setCurrency("PEN");
            response.setAccountType(accountDetails.getAccountType());
            response.setLastMovementDate(accountDetails.getLastMovementDate());
            return response;
          })
      );
  }

  @Override
  public Mono<CardMovementsResponse> getCardMovements(String cardId, Integer limit) {
    log.info("Getting card movements for card: {} limit: {}", cardId, limit);

    int queryLimit = limit != null ? Math.min(limit, 50) : 10;

    return cardMovementRepository.findByCardIdOrderByProcessedAtDesc(cardId, queryLimit)
      .collectList()
      .flatMap(movements -> {
        if (movements.isEmpty()) {
          // Check if card exists
          return debitCardRepository.existsById(cardId)
            .flatMap(debitExists -> {
              if (debitExists) {
                return Mono.just(buildEmptyMovementsResponse(cardId, "DEBIT"));
              }
              return creditCardRepository.existsById(cardId)
                .flatMap(creditExists -> {
                  if (creditExists) {
                    return Mono.just(buildEmptyMovementsResponse(cardId, "CREDIT"));
                  }
                  return Mono.error(new CardNotFoundException("Card not found: " + cardId));
                });
            });
        }

        // Determine card type from first movement or check repositories
        return determineCardType(cardId)
          .map(cardType -> {
            CardMovementsResponse response = new CardMovementsResponse();
            response.setCardId(cardId);
            response.setCardType(cardType);
            response.setMovements(movements);
            response.setTotalCount(movements.size());
            response.setRetrievedAt(LocalDateTime.now());
            return response;
          });
      });
  }

  @Override
  public Mono<ProductEligibilityResponse> checkCustomerProductEligibility(String customerId) {
    log.info("Checking product eligibility for customer: {}", customerId);

    return creditCardRepository.findByCustomerId(customerId)
      .collectList()
      .map(creditCards -> {
        ProductEligibilityResponse response = new ProductEligibilityResponse();
        response.setCustomerId(customerId);

        List<String> eligibilityReasons = new ArrayList<>();
        List<String> ineligibilityReasons = new ArrayList<>();
        List<OverdueProduct> overdueProducts = new ArrayList<>();

        boolean hasOverdueDebt = false;

        for (CreditCard creditCard : creditCards) {
          if (creditCard.getDaysPastDue() != null && creditCard.getDaysPastDue() > 0) {
            hasOverdueDebt = true;
            ineligibilityReasons.add("OVERDUE_CREDIT_DEBT");

            OverdueProduct overdueProduct = new OverdueProduct();
            overdueProduct.setProductId(creditCard.getCardId());
            overdueProduct.setProductType("CREDIT_CARD");
            overdueProduct.setOverdueAmount(creditCard.getCurrentBalance());
            overdueProduct.setDaysPastDue(creditCard.getDaysPastDue());
            overdueProducts.add(overdueProduct);
          }
        }

        if (!hasOverdueDebt) {
          eligibilityReasons.add("NO_OVERDUE_DEBTS");
          eligibilityReasons.add("GOOD_PAYMENT_HISTORY");
        }

        response.setIsEligible(!hasOverdueDebt);
        response.setEligibilityReasons(eligibilityReasons);
        response.setIneligibilityReasons(ineligibilityReasons);
        response.setOverdueProducts(overdueProducts);
        response.setValidatedAt(LocalDateTime.now());

        return response;
      });
  }

  @Override
  public Mono<CustomerCardsSummaryResponse> getCustomerCardsSummary(String customerId) {
    log.info("Getting cards summary for customer: {}", customerId);

    Mono<List<CreditCardSummary>> creditCardsMono = creditCardRepository.findByCustomerId(customerId)
      .map(this::mapToCreditCardSummary)
      .collectList();

    Mono<List<DebitCardSummary>> debitCardsMono = debitCardRepository.findByCustomerId(customerId)
      .map(this::mapToDebitCardSummary)
      .collectList();

    return Mono.zip(creditCardsMono, debitCardsMono)
      .map(tuple -> {
        List<CreditCardSummary> creditCards = tuple.getT1();
        List<DebitCardSummary> debitCards = tuple.getT2();

        CustomerCardsSummaryResponse response = new CustomerCardsSummaryResponse();
        response.setCustomerId(customerId);
        response.setCreditCards(creditCards);
        response.setDebitCards(debitCards);

        int activeCards = (int) (creditCards.stream().filter(CreditCardSummary::getIsActive).count() +
          debitCards.stream().filter(DebitCardSummary::getIsActive).count());

        response.setTotalActiveCards(activeCards);
        response.setRetrievedAt(LocalDateTime.now());

        return response;
      });
  }

  // Helper methods
  private Mono<CardMovement> saveCardMovement(String cardId, DebitPurchaseRequest request, String transactionId, String status) {
    CardMovement movement = new CardMovement();
    movement.setTransactionId(transactionId);
    movement.setCardId(cardId);
    movement.setAmount(request.getAmount());
    movement.setTransactionType(request.getTransactionType());
    movement.setMerchantInfo(request.getMerchantInfo());
    movement.setProcessedAt(LocalDateTime.now());
    movement.setStatus(status);

    return cardMovementRepository.save(movement);
  }

  private DebitPurchaseResponse buildSuccessResponse(DebitCard debitCard, DebitPurchaseRequest request,
                                                     String transactionId, List<AccountUsed> accountsUsed) {
    DebitPurchaseResponse response = new DebitPurchaseResponse();
    response.setSuccess(true);
    response.setTransactionId(transactionId);
    response.setCardId(debitCard.getCardId());
    response.setRequestedAmount(request.getAmount());
    response.setProcessedAmount(request.getAmount());
    response.setAccountsUsed(accountsUsed);
    response.setProcessedAt(LocalDateTime.now());
    return response;
  }

  private Mono<DebitPurchaseResponse> buildErrorResponse(Throwable throwable) {
    DebitPurchaseResponse response = new DebitPurchaseResponse();
    response.setSuccess(false);

    if (throwable instanceof InsufficientFundsException) {
      response.setErrorCode("INSUFFICIENT_FUNDS");
    } else if (throwable instanceof InvalidAmountException) {
      response.setErrorCode("INVALID_AMOUNT");
    } else if (throwable instanceof CardNotFoundException) {
      response.setErrorCode("CARD_INACTIVE");
    } else {
      response.setErrorCode("ACCOUNT_UNAVAILABLE");
    }

    response.setErrorMessage(throwable.getMessage());
    response.setProcessedAt(LocalDateTime.now());

    return Mono.just(response);
  }

  private CardMovementsResponse buildEmptyMovementsResponse(String cardId, String cardType) {
    CardMovementsResponse response = new CardMovementsResponse();
    response.setCardId(cardId);
    response.setCardType(cardType);
    response.setMovements(new ArrayList<>());
    response.setTotalCount(0);
    response.setRetrievedAt(LocalDateTime.now());
    return response;
  }

  private Mono<String> determineCardType(String cardId) {
    return debitCardRepository.existsById(cardId)
      .flatMap(debitExists -> {
        if (debitExists) {
          return Mono.just("DEBIT");
        }
        return creditCardRepository.existsById(cardId)
          .map(creditExists -> creditExists ? "CREDIT" : "DEBIT");
      });
  }

  private CreditCardSummary mapToCreditCardSummary(CreditCard creditCard) {
    CreditCardSummary summary = new CreditCardSummary();
    summary.setCardId(creditCard.getCardId());
    summary.setCardNumber(creditCard.getCardNumber());
    summary.setCreditLimit(creditCard.getCreditLimit());
    summary.setAvailableCredit(creditCard.getAvailableCredit());
    summary.setCurrentBalance(creditCard.getCurrentBalance());
    summary.setIsActive(creditCard.getIsActive());
    return summary;
  }

  private DebitCardSummary mapToDebitCardSummary(DebitCard debitCard) {
    DebitCardSummary summary = new DebitCardSummary();
    summary.setCardId(debitCard.getCardId());
    summary.setCardNumber(debitCard.getCardNumber());
    summary.setPrimaryAccountId(debitCard.getPrimaryAccountId());
    summary.setAssociatedAccountsCount(debitCard.getAssociatedAccountIds().size());
    summary.setIsActive(debitCard.getIsActive());
    return summary;
  }
}

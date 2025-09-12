package com.bootcamp.ntt.card_service.mapper;

import com.bootcamp.ntt.card_service.client.dto.account.AccountBalanceResponse;
import com.bootcamp.ntt.card_service.client.dto.account.AccountDetailsResponse;
import com.bootcamp.ntt.card_service.client.dto.account.AccountUsage;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionAccount;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.entity.DebitCard;
import com.bootcamp.ntt.card_service.model.DebitCardCreateRequest;
import com.bootcamp.ntt.card_service.model.DebitCardResponse;
import com.bootcamp.ntt.card_service.model.DebitCardUpdateRequest;
import com.bootcamp.ntt.card_service.model.DebitPurchaseRequest;
import com.bootcamp.ntt.card_service.model.DebitPurchaseResponse;
import com.bootcamp.ntt.card_service.model.DebitPurchaseResponseAccountsUsedInner;
import com.bootcamp.ntt.card_service.model.PrimaryAccountBalanceResponse;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class DebitCardMapper {

  public DebitCard toEntity(DebitCardCreateRequest dto, String cardNumber) {
    if (dto == null) {
      return null;
    }

    DebitCard card = new DebitCard();
    card.setCardNumber(cardNumber);
    card.setCustomerId(dto.getCustomerId());
    card.setPrimaryAccountId(dto.getPrimaryAccountId());
    card.setAssociatedAccountIds(dto.getAssociatedAccountIds());
    card.setActive(true);
    return card;
  }

  public DebitCard updateEntity(DebitCard existing, DebitCardUpdateRequest dto) {
    if (existing == null || dto == null) {
      return existing;
    }

    if (dto.getPrimaryAccountId() != null) {
      existing.setPrimaryAccountId(dto.getPrimaryAccountId());
    }

    if (dto.getAssociatedAccountIds() != null) {
      existing.setAssociatedAccountIds(dto.getAssociatedAccountIds());
    }

    return existing;
  }

  public DebitCardResponse toResponse(DebitCard entity) {
    if (entity == null) {
      return null;
    }

    DebitCardResponse response = new DebitCardResponse();
    response.setId(entity.getId());
    response.setCardNumber(entity.getCardNumber());
    response.setCustomerId(entity.getCustomerId());
    response.setPrimaryAccountId(entity.getPrimaryAccountId());
    response.setAssociatedAccountIds(entity.getAssociatedAccountIds());
    response.setIsActive(entity.isActive());
    response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

    return response;
  }

  public List<TransactionAccount> toTransactionAccounts(List<AccountUsage> accountsUsed) {
    if (accountsUsed == null) {
      return List.of();
    }

    return accountsUsed.stream()
      .filter(usage -> usage.getAmountDeducted() > 0)
      .map(usage -> {
        TransactionAccount account = new TransactionAccount();
        account.setAccountId(usage.getAccountId());
        account.setAmountDeducted(usage.getAmountDeducted());
        return account;
      })
      .collect(Collectors.toList());
  }

  public DebitPurchaseResponse toDebitPurchaseResponse(DebitCard debitCard,
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

  public PrimaryAccountBalanceResponse toPrimaryAccountBalanceResponse(
    String cardId,
    DebitCard debitCard,
    AccountBalanceResponse accountBalance,
    AccountDetailsResponse accountDetails) {

    PrimaryAccountBalanceResponse response = new PrimaryAccountBalanceResponse();
    response.setCardId(cardId);
    response.setPrimaryAccountId(debitCard.getPrimaryAccountId());
    response.setAccountNumber(accountDetails.getAccountNumber());
    response.setBalance(accountBalance.getAvailableBalance());
    response.setCurrency(accountDetails.getCurrency());

    try {
      response.setAccountType(
        PrimaryAccountBalanceResponse.AccountTypeEnum.valueOf(accountDetails.getAccountType())
      );
    } catch (IllegalArgumentException e) {
      response.setAccountType(PrimaryAccountBalanceResponse.AccountTypeEnum.SAVINGS);
    }

    if (accountDetails.getLastMovementDate() != null) {
      response.setLastMovementDate(accountDetails.getLastMovementDate());
    }

    return response;
  }

  public String mapTransactionType(DebitPurchaseRequest.TransactionTypeEnum transactionType) {
    if (transactionType == null) {
      return "DEBIT_TRANSACTION";
    }

    switch (transactionType) {
      case PURCHASE:
        return "DEBIT_PURCHASE";
      case WITHDRAWAL:
        return "DEBIT_WITHDRAWAL";
      case PAYMENT:
        return "DEBIT_PAYMENT";
      default:
        return "DEBIT_TRANSACTION";
    }
  }

  public TransactionRequest toTransactionRequest(DebitCard debitCard, DebitPurchaseRequest request,
                                                 List<AccountUsage> accountsUsed, String authCode) {
    TransactionRequest transactionRequest = new TransactionRequest();
    transactionRequest.setCardId(debitCard.getId());
    transactionRequest.setAmount(request.getAmount());
    transactionRequest.setTransactionType(mapTransactionType(request.getTransactionType()));
    transactionRequest.setAuthorizationCode(authCode);
    transactionRequest.setStatus("APPROVED");
    transactionRequest.setTimestamp(LocalDateTime.now());

    if (accountsUsed != null && !accountsUsed.isEmpty()) {
      transactionRequest.setAccountsAffected(toTransactionAccounts(accountsUsed));
    }

    /*String description = String.format("%s - %s account(s) used",
      request.getTransactionType(), accountsUsed != null ? accountsUsed.size() : 0);
    transactionRequest.setDescription(description);*/

    return transactionRequest;
  }
}

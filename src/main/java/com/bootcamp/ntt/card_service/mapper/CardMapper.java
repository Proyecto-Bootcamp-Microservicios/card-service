package com.bootcamp.ntt.card_service.mapper;


import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionAccount;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionResponse;
import com.bootcamp.ntt.card_service.model.CardMovement;
import com.bootcamp.ntt.card_service.model.CardMovementAccountsAffectedInner;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CardMapper {

  public CardMovement toCardMovement(TransactionResponse transaction) {
    if (transaction == null) {
      return null;
    }

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
      // log en service
    }

    movement.setMerchantInfo(transaction.getMerchantInfo());
    movement.setProcessedAt(transaction.getProcessedAt());

    if (transaction.getAccountsAffected() != null) {
      movement.setAccountsAffected(toCardMovementAccountsAffected(transaction.getAccountsAffected()));
    }

    return movement;
  }

  public List<CardMovementAccountsAffectedInner> toCardMovementAccountsAffected(
    List<TransactionAccount> accountsAffected) {

    if (accountsAffected == null) {
      return Collections.emptyList();
    }

    return accountsAffected.stream()
      .map(account -> {
        CardMovementAccountsAffectedInner affected = new CardMovementAccountsAffectedInner();
        affected.setAccountId(account.getAccountId());
        affected.setAmountDeducted(account.getAmountDeducted());
        return affected;
      })
      .collect(Collectors.toList());
  }

  public List<CardMovement> toCardMovementList(List<TransactionResponse> transactions) {
    if (transactions == null) {
      return Collections.emptyList();
    }

    return transactions.stream()
      .map(this::toCardMovement)
      .collect(Collectors.toList());
  }
}

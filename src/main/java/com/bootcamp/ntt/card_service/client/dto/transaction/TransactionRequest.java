package com.bootcamp.ntt.card_service.client.dto.transaction;

import lombok.Data;
import java.util.List;

@Data
public class TransactionRequest {
  private String cardId;
  private Double amount;
  private String transactionType;
  private String merchantInfo;
  private List<TransactionAccount> accountsAffected;
}

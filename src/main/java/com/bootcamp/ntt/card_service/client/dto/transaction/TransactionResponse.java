package com.bootcamp.ntt.card_service.client.dto.transaction;

import java.time.OffsetDateTime;
import java.util.List;


import lombok.Data;

@Data
public class TransactionResponse {
  private String transactionId;
  private String cardId;
  private Double amount;
  private String transactionType;
  private String merchantInfo;
  private List<TransactionAccount> accountsAffected;
  private String status;
  private OffsetDateTime processedAt;
}

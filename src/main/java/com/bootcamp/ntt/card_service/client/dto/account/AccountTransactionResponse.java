package com.bootcamp.ntt.card_service.client.dto.account;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class AccountTransactionResponse {
  private String transactionId;
  private String accountId;
  private Double amount;
  private String transactionType;
  private String status;
  private OffsetDateTime processedAt;
}

package com.bootcamp.ntt.card_service.client.dto.transaction;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class TransactionRequest {
  private String cardId;
  private String cardNumber;
  private Double amount;
  private String transactionType;
  private String authorizationCode;
  private String status;
  private String description;
  private LocalDateTime timestamp;
  //private String merchantInfo;
  private List<AccountUsage> accountsAffected;
}

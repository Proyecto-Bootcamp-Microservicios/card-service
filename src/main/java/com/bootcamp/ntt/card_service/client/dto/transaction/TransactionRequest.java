package com.bootcamp.ntt.card_service.client.dto.transaction;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransactionRequest {
  private String cardId;
  private Double amount;
  private String transactionType;
  private String authorizationCode;
  private String status;
  private LocalDateTime timestamp;
  //private String merchantInfo;
  private List<TransactionAccount> accountsAffected;
}

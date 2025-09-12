package com.bootcamp.ntt.card_service.client.dto.account;

import lombok.Data;

@Data
public class AccountDebitRequest {
  private Double amount;
  private String description;
  private String transactionReference;

  public AccountDebitRequest() { }

  public AccountDebitRequest(Double amount, String description, String transactionReference) {
    this.amount = amount;
    this.description = description;
    this.transactionReference = transactionReference;
  }
}

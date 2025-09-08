package com.bootcamp.ntt.card_service.client.dto.account;

import lombok.Data;

@Data
public class AccountBalanceResponse {
  private String accountId;
  private Double availableBalance;
  private Double currentBalance;
  private String currency;
}

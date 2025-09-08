package com.bootcamp.ntt.card_service.client.dto.account;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUsage {
  private String accountId;
  private Double amountDeducted;
  private Double remainingBalance;
}

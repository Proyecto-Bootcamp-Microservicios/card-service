package com.bootcamp.ntt.card_service.client.dto.account;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class AccountDetailsResponse {
  private String accountId;
  private String accountNumber;
  private String accountType; // "SAVINGS", "CHECKING"
  private String currency;
  private OffsetDateTime lastTransactionDate;
  private OffsetDateTime lastMovementDate;
}

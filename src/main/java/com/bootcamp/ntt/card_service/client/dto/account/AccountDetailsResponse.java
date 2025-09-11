package com.bootcamp.ntt.card_service.client.dto.account;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class AccountDetailsResponse {
  private String accountId;
  private String accountNumber;
  private String accountType; // "SAVINGS", "CHECKING"
  private String currency;
  private OffsetDateTime lastTransactionDate;
  private OffsetDateTime lastMovementDate;
}

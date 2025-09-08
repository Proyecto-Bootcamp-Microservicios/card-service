package com.bootcamp.ntt.card_service.client.dto.account;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class AccountTransactionResponse {
  private String transactionId;
  private String accountId;
  private Double amount;
  private String transactionType;
  private String status;
  private OffsetDateTime processedAt;
}

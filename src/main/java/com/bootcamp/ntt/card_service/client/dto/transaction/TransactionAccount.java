package com.bootcamp.ntt.card_service.client.dto.transaction;
import lombok.Data;

@Data
public class TransactionAccount {
  private String accountId;
  private Double amountDeducted;
}

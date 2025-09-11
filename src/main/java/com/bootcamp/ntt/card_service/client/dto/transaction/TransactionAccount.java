package com.bootcamp.ntt.card_service.client.dto.transaction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransactionAccount {
  private String accountId;
  private Double amountDeducted;
}

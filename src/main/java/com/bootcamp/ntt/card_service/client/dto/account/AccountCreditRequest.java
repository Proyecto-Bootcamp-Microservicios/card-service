package com.bootcamp.ntt.card_service.client.dto.account;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AccountCreditRequest {
  private BigDecimal amount;
  private String description;
}

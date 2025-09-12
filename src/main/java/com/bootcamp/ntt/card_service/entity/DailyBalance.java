package com.bootcamp.ntt.card_service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "daily_balances")
@CompoundIndex(def = "{'customerId': 1, 'cardId': 1, 'date': 1}", unique = true)
public class DailyBalance {

  @Id
  private String id;

  @Indexed
  private String customerId;

  @Indexed
  private String cardId;

  private String cardNumber;

  @Indexed
  private LocalDate date;

  private BigDecimal currentBalance;
  private BigDecimal availableCredit;
  private BigDecimal creditLimit;

  private LocalDateTime capturedAt;
}

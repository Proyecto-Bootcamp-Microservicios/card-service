package com.bootcamp.ntt.card_service.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

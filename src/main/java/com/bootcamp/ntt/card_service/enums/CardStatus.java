package com.bootcamp.ntt.card_service.enums;

import lombok.ToString;

@ToString
public enum CardStatus {
  ACTIVE,
  INACTIVE,
  CHARGE_PENDING,
  BLOCKED,
  EXPIRED
}

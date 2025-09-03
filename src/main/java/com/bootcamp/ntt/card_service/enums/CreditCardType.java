package com.bootcamp.ntt.card_service.enums;

import lombok.ToString;

@ToString
public enum CreditCardType {
  PERSONAL,
  ENTERPRISE;

  public static CardType fromString(String value) {
    if (value != null) {
      for (CardType type : CardType.values()) {
        if (type.name().equalsIgnoreCase(value)) {
          return type;
        }
      }
    }
    return null; // o excepcion
  }
}

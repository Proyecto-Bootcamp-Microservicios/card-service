package com.bootcamp.ntt.card_service.entity;

import lombok.ToString;

@ToString
public enum CardType {
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

package com.bootcamp.ntt.card_service.exception;

import lombok.Getter;

@Getter
public class BusinessRuleException extends RuntimeException {

  private final String code;

  public BusinessRuleException(String code, String message) {
    super(message);
    this.code = code;
  }
}

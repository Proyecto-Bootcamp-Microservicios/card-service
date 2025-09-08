package com.bootcamp.ntt.card_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CardServiceException extends RuntimeException {
  private final String errorCode;
  private final HttpStatus httpStatus;

  public CardServiceException(String message, String errorCode, HttpStatus httpStatus) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }
}

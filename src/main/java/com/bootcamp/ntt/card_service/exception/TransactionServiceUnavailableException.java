package com.bootcamp.ntt.card_service.exception;

public class TransactionServiceUnavailableException extends RuntimeException {
  public TransactionServiceUnavailableException(String message) {
    super(message);
  }
}

package com.bootcamp.ntt.card_service.exception;


public class TransactionServiceException extends RuntimeException {
  public TransactionServiceException(String message) {
    super(message);
  }
}

package com.bootcamp.ntt.card_service.exception;

public class CustomerServiceUnavailableException extends RuntimeException {
  public CustomerServiceUnavailableException(String message) {
    super(message);
  }
}

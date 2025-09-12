package com.bootcamp.ntt.card_service.utils;

public final class ErrorCodes {

  private ErrorCodes() {
    throw new IllegalStateException("Utility class");
  }

  public static final String CARD_NOT_FOUND = "CARD_NOT_FOUND";
  public static final String CARD_INACTIVE = "CARD_INACTIVE";
  public static final String ACCOUNT_ALREADY_ASSOCIATED = "ACCOUNT_ALREADY_ASSOCIATED";
  public static final String INVALID_ACCOUNT_OWNERSHIP = "INVALID_ACCOUNT_OWNERSHIP";
}

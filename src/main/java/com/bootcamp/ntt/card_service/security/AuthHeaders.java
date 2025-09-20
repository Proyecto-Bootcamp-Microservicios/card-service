package com.bootcamp.ntt.card_service.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthHeaders {
  private final String customerId;
  private final String role;
  private final String userId;

  public boolean isAdmin() {
    return "ADMIN".equals(role);
  }

  public boolean hasCustomerId(String targetCustomerId) {
    return customerId != null && customerId.equals(targetCustomerId);
  }
}

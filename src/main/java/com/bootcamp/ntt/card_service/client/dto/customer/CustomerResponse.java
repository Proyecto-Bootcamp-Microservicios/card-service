package com.bootcamp.ntt.card_service.client.dto.customer;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class CustomerResponse {
  private String customerId;
  private String firstName;
  private String lastName;
  private String documentNumber;
  private String customerType; // "PERSONAL", "BUSINESS"
  private String email;
  private String phone;
  private Boolean isActive;
  private OffsetDateTime createdAt;
}

package com.bootcamp.ntt.card_service.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CustomerTypeResponse {
  @JsonProperty("id")
  private String id;

  @JsonProperty("customerType")
  private String customerType; // "PERSONAL" o "ENTERPRISE"
}

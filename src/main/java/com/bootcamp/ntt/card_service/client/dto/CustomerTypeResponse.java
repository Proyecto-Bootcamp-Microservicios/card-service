package com.bootcamp.ntt.card_service.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CustomerTypeResponse {
  @JsonProperty("customerId")
  private String customerId;

  @JsonProperty("type")
  private String type; // "PERSONAL" o "ENTERPRISE"
}

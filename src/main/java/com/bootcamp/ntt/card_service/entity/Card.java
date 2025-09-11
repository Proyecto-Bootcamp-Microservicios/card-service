package com.bootcamp.ntt.card_service.entity;

import com.bootcamp.ntt.card_service.enums.CardType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cards")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "cardType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CreditCard.class, name = "CREDIT"),
  @JsonSubTypes.Type(value = DebitCard.class, name = "DEBIT")
})
public abstract class Card {

  @Id
  private String id;

  @NotBlank(message = "El número de la tarjeta es obligatorio")
  @Pattern(
    regexp = "^\\d{16}$",
    message = "El número de tarjeta debe tener 16 dígitos"
  )
  @Indexed(unique = true)
  @Field("cardNumber")
  private String cardNumber;

  @NotBlank(message = "El ID del cliente es obligatorio")
  @Indexed
  @Field("customerId")
  private String customerId;

  @NotNull(message = "El tipo de tarjeta es obligatorio")
  @Field("type")
  private CardType type;

  @NotNull(message = "El estado de la tarjeta es obligatorio")
  @Field("isActive")
  private boolean isActive;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;

  public abstract CardType getCardType();
}

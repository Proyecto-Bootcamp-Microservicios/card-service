package com.bootcamp.ntt.card_service.entity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cards")
public class Card {

  @Id
  private String id;

  @NotBlank(message = "El número de la tarjeta es obligatorio")
  @Pattern(
    regexp = "^\\d{16}$",
    message = "El número de tarjeta debe tener 16 dígitos"
  )  @Indexed(unique = true)
  @Field("cardNumber")
  private String cardNumber;

  @NotBlank(message = "El ID del cliente es obligatorio")
  @Indexed
  @Field("customerId")
  private String customerId;

  @NotNull(message = "El tipo de tarjeta es obligatorio")
  @Field("type")
  private CardType type;

  @NotNull(message = "El límite de crédito de la tarjera es obligatorio")
  @DecimalMin(value = "0.01", message = "El límite de crédito de la tarjeta debe ser mayor a 0")
  @Digits(integer = 12, fraction = 2, message = "El límite debe tener máximo 12 enteros y 2 decimales")
  @Field("creditLimit")
  private BigDecimal creditLimit;

  @NotNull(message = "El crédito disponible de la tarjeta es obligatorio")
  @DecimalMin(value = "0.00", message = "El crédito disponible de la tarjeta no puede ser negativo")
  @Field("availableCredit")
  private BigDecimal availableCredit;

  @NotNull(message = "El crédito consumido de la tarjeta es obligatorio")
  @DecimalMin(value = "0.00", message = "El crédito consumido de la tarjeta no puede ser negativo")
  @Field("currentBalance")
  private BigDecimal currentBalance;

  @NotNull(message = "El estado de la tarjeta es obligatorio")
  @Field("isActive")
  private boolean isActive;

  @CreatedDate
  @Field("createdAt")
  private Instant createdAt;

  @LastModifiedDate
  @Field("updatedAt")
  private Instant updatedAt;

}

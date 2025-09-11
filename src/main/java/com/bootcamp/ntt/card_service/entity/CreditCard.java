package com.bootcamp.ntt.card_service.entity;

import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.enums.CreditCardType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cards")
public class CreditCard extends Card {

  @NotNull(message = "El tipo de tarjeta de crédito es obligatorio")
  @Field("creditCardType")
  private CreditCardType creditCardType; // PERSONAL, ENTERPRISE

  @NotNull(message = "El límite de crédito es obligatorio")
  @DecimalMin(value = "0.01", message = "El límite de crédito debe ser mayor a 0")
  @Field("creditLimit")
  private BigDecimal creditLimit;

  @NotNull(message = "El crédito disponible es obligatorio")
  @DecimalMin(value = "0.00", message = "El crédito disponible no puede ser negativo")
  @Field("availableCredit")
  private BigDecimal availableCredit;

  @NotNull(message = "El balance actual es obligatorio")
  @DecimalMin(value = "0.00", message = "El balance actual no puede ser negativo")
  @Field("currentBalance")
  private BigDecimal currentBalance;

  @Field("paymentDueDate")
  private LocalDate paymentDueDate;

  @Field("minimumPayment")
  private BigDecimal minimumPayment;

  @Field("isOverdue")
  private Boolean isOverdue;

  @Field("overdueDays")
  private Integer overdueDays;

  @Override
  public CardType getCardType() {
    return CardType.CREDIT;
  }

}

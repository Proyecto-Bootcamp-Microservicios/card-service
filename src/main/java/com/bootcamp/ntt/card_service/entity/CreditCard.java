package com.bootcamp.ntt.card_service.entity;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.enums.CreditCardType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cards")
public class CreditCard extends Card {

  @NotNull(message = "El tipo de tarjeta de crédito es obligatorio")
  @Field("type")
  private CreditCardType creditCardtype; // PERSONAL, ENTERPRISE

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

  @Override
  public CardType getCardType() {
    return CardType.CREDIT;
  }

}

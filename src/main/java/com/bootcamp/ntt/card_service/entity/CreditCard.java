package com.bootcamp.ntt.card_service.entity;

import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.enums.CreditCardType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cards")
public class CreditCard extends Card {

  @NotNull(message = "El tipo de tarjeta de crédito es obligatorio")
  @Field("creditCardType")
  private CreditCardType creditCardType;

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
  @PastOrPresent(message = "La fecha de vencimiento no puede ser futura")
  private LocalDate paymentDueDate;

  @Field("minimumPayment")
  @DecimalMin(value = "0.01", message = "El pago mínimo debe ser mayor a 0")
  private BigDecimal minimumPayment;

  @Field("isOverdue")
  private Boolean isOverdue;

  @Field("overdueDays")
  private Integer overdueDays;


  @Override
  public CardType getCardType() {
    return CardType.CREDIT;
  }
  /*
  public BigDecimal calculateMinimumPayment() {
    if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal percentagePayment = currentBalance.multiply(new BigDecimal("0.05"));
    BigDecimal minimumAmount = new BigDecimal("25.00");

    if (currentBalance.compareTo(minimumAmount) < 0) {
      return currentBalance;
    }

    return percentagePayment.max(minimumAmount);
  }
  public void updatePaymentInfo() {
    this.minimumPayment = calculateMinimumPayment();

    if (paymentDueDate != null) {
      LocalDate today = LocalDate.now();
      this.isOverdue = today.isAfter(paymentDueDate) &&
        minimumPayment.compareTo(BigDecimal.ZERO) > 0;
      this.overdueDays = this.isOverdue ?
        (int) paymentDueDate.until(today, ChronoUnit.DAYS) : 0;
    }
  }*/

}

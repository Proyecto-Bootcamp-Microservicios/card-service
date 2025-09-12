package com.bootcamp.ntt.card_service.entity;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.bootcamp.ntt.card_service.enums.CardType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cards")
public class DebitCard extends Card {

  @NotBlank(message = "La cuenta principal es obligatoria")
  @Field("primaryAccountId")
  private String primaryAccountId;

  @Field("associatedAccountIds")
  private List<String> associatedAccountIds = new ArrayList<>();

  @Override
  public CardType getCardType() {
    return CardType.DEBIT;
  }
}

package com.bootcamp.ntt.card_service.entity;

import com.bootcamp.ntt.card_service.enums.CardType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;

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

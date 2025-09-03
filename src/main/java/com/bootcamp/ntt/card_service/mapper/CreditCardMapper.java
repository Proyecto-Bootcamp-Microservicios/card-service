package com.bootcamp.ntt.card_service.mapper;

import com.bootcamp.ntt.card_service.entity.Card;
import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.enums.CreditCardType;
import com.bootcamp.ntt.card_service.model.CardCreateRequest;
import com.bootcamp.ntt.card_service.model.CardResponse;
import com.bootcamp.ntt.card_service.model.CardUpdateRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneOffset;

@Component
public class CreditCardMapper {

  public CreditCard toEntity(CardCreateRequest dto, String customerType, String cardNumber) {
    if (dto == null) {
      return null;
    }

    CreditCard card = new CreditCard();
    card.setCardNumber(cardNumber);
    card.setCustomerId(dto.getCustomerId());
    card.setCreditCardtype(CreditCardType.valueOf(customerType));
    card.setCreditLimit(BigDecimal.valueOf(dto.getCreditLimit()));
    card.setAvailableCredit(
      dto.getAvailableCredit() != null ? BigDecimal.valueOf(dto.getAvailableCredit()) : BigDecimal.ZERO
    );
    card.setCurrentBalance(
      dto.getCurrentBalance() != null ? BigDecimal.valueOf(dto.getCurrentBalance()) : BigDecimal.ZERO
    );
    card.setActive(true);
    return card;
  }


  public CreditCard updateEntity(CreditCard existing, CardUpdateRequest dto) {
    if (existing == null || dto == null) {
      return existing;
    }


    if (dto.getCreditLimit() != null) {
      existing.setCreditLimit(BigDecimal.valueOf(dto.getCreditLimit()));
    }

    if (dto.getAvailableCredit() != null) {
      existing.setAvailableCredit(BigDecimal.valueOf(dto.getAvailableCredit()));
    }

    if (dto.getCurrentBalance() != null) {
      existing.setCurrentBalance(BigDecimal.valueOf(dto.getCurrentBalance()));
    }

    if (dto.getIsActive() != null) {
      existing.setActive(dto.getIsActive());
    }
    return existing;
  }

  public CardResponse toResponse(Card entity) {
    if (entity == null) {
      return null;
    }

    CardResponse response = new CardResponse();
    response.setId(entity.getId());
    response.setCardNumber(entity.getCardNumber());
    response.setCustomerId(entity.getCustomerId());
    response.setType(CardResponse.TypeEnum.valueOf(entity.getType().name()));
    response.setCreditLimit(entity.getCreditLimit().doubleValue());
    response.setAvailableCredit(entity.getAvailableCredit().doubleValue());
    response.setCurrentBalance(entity.getCurrentBalance().doubleValue());
    response.setIsActive(entity.isActive());

    response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

    return response;
  }

}

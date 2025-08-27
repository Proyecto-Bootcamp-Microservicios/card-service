package com.bootcamp.ntt.card_service.mapper;

import com.bootcamp.ntt.card_service.entity.Card;
import com.bootcamp.ntt.card_service.entity.CardType;
import com.bootcamp.ntt.card_service.model.CardCreateRequest;
import com.bootcamp.ntt.card_service.model.CardResponse;
import com.bootcamp.ntt.card_service.model.CardUpdateRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneOffset;

@Component
public class CardMapper {

  public Card toEntity(CardCreateRequest dto, String customerType) {
    if (dto == null) {
      return null;
    }

    Card card = new Card();
    card.setCardNumber(dto.getCardNumber());
    card.setCustomerId(dto.getCustomerId());
    card.setType(CardType.valueOf(customerType));
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


  public Card updateEntity(Card existing, CardUpdateRequest dto) {
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

package com.bootcamp.ntt.card_service.mapper;

import com.bootcamp.ntt.card_service.entity.DebitCard;
import com.bootcamp.ntt.card_service.model.DebitCardCreateRequest;
import com.bootcamp.ntt.card_service.model.DebitCardResponse;
import com.bootcamp.ntt.card_service.model.DebitCardUpdateRequest;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

@Component
public class DebitCardMapper {

  public DebitCard toEntity(DebitCardCreateRequest dto, String cardNumber) {
    if (dto == null) {
      return null;
    }

    DebitCard card = new DebitCard();
    card.setCardNumber(cardNumber);
    card.setCustomerId(dto.getCustomerId());
    card.setPrimaryAccountId(dto.getPrimaryAccountId());
    card.setAssociatedAccountIds(dto.getAssociatedAccountIds());
    card.setActive(true);
    return card;
  }

  public DebitCard updateEntity(DebitCard existing, DebitCardUpdateRequest dto) {
    if (existing == null || dto == null) {
      return existing;
    }

    if (dto.getPrimaryAccountId() != null) {
      existing.setPrimaryAccountId(dto.getPrimaryAccountId());
    }

    if (dto.getAssociatedAccountIds() != null) {
      existing.setAssociatedAccountIds(dto.getAssociatedAccountIds());
    }

    return existing;
  }

  public DebitCardResponse toResponse(DebitCard entity) {
    if (entity == null) {
      return null;
    }

    DebitCardResponse response = new DebitCardResponse();
    response.setId(entity.getId());
    response.setCardNumber(entity.getCardNumber());
    response.setCustomerId(entity.getCustomerId());
    response.setPrimaryAccountId(entity.getPrimaryAccountId());
    response.setAssociatedAccountIds(entity.getAssociatedAccountIds());
    response.setIsActive(entity.isActive());
    response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

    return response;
  }
}

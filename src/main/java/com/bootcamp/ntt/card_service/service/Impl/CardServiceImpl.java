package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerClient;
import com.bootcamp.ntt.card_service.entity.Card;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.mapper.CardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.repository.CardRepository;
import com.bootcamp.ntt.card_service.service.CardService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

  private final CardRepository cardRepository;
  private final CardMapper cardMapper;
  private final CustomerClient customerClient;

  @Override
  public Flux<CardResponse> getAllCards(Boolean isActive) {
    return cardRepository.findAll()
      .map(cardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards retrieved"));
  }

  @Override
  public Flux<CardResponse> getCardsByActive(Boolean isActive) {
    return cardRepository.findByIsActive(isActive)
      .map(cardMapper::toResponse)
      .doOnComplete(() -> log.debug("Active cards retrieved"));
  }

  @Override
  public Flux<CardResponse> getCardsByActiveAndCustomer(Boolean isActive, String customerId) {
    return cardRepository.findByIsActiveAndCustomerId(isActive, customerId)
      .map(cardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards active by customer retrieved"));
  }

  @Override
  public Mono<CardResponse> getCardById(String id) {
    log.debug("Getting credit card by ID: {}", id);
    return cardRepository.findById(id)
      .map(cardMapper::toResponse)
      .doOnSuccess(credit -> {
        if (credit != null) {
          log.debug("Card found with ID: {}", id);
        } else {
          log.debug("Card not found with ID: {}", id);
        }
      });
  }

  @Override
  public Mono<CardResponse> createCard(CardCreateRequest cardRequest) {
    /*log.debug("Creating card for customer: {}", cardRequest.getCustomerId());

    return customerClient.getCustomerType(cardRequest.getCustomerId())
      .flatMap(customerType -> validateCreditCreation(cardRequest.getCustomerId(), customerType.getType())
        .then(Mono.just(cardRequest))
        .map(request -> cardMapper.toEntity(request, customerType.getType())) // Pasamos el tipo
        .flatMap(cardRepository::save)
        .map(cardMapper::toResponse))
      .doOnSuccess(response -> log.debug("Card created with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error creating card: {}", error.getMessage()));*/
    return null;
  }

  @Override
  public Mono<CardResponse> updateCard(String id, CardUpdateRequest cardRequest) {
    log.debug("Updating card with ID: {}", id);

    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .map(existing -> cardMapper.updateEntity(existing, cardRequest))
      .flatMap(cardRepository::save)
      .map(cardMapper::toResponse)
      .doOnSuccess(response -> log.debug("Card updated with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error updating card {}: {}", id, error.getMessage()));
  }

  @Override
  public Mono<Void> deleteCard(String id) {
    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .flatMap(cardRepository::delete)
      .doOnSuccess(unused -> log.debug("Card deleted"))
      .doOnError(error -> log.error("Error deleting card {}: {}", id, error.getMessage()));
  }

  @Override
  public Mono<CardResponse> deactivateCard(String id) {
    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(false);  // soft delete
        return cardRepository.save(card);
      })
      .map(cardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Card {} deactivated", id))
      .doOnError(e -> log.error("Error deactivating card {}: {}", id, e.getMessage()));
  }

  @Override
  public Mono<CardResponse> activateCard(String id) {
    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(true);  // reactivar
        return cardRepository.save(card);
      })
      .map(cardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Card {} activated", id))
      .doOnError(e -> log.error("Error activating card {}: {}", id, e.getMessage()));
  }

  @Override
  public Mono<ChargeAuthorizationResponse> authorizeCharge(String cardNumber, ChargeAuthorizationRequest request) {
    return cardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + cardNumber)))
      .flatMap(creditCard -> validateAndProcessCharge(creditCard, request.getAmount()));
  }

  private Mono<ChargeAuthorizationResponse> validateAndProcessCharge(Card card, Double amount) {

    Double availableCredit = card.getAvailableCredit().doubleValue();

    // 1. Validar estado de la tarjeta
    if (!card.isActive()) {
      return Mono.just(createDeclinedResponse(availableCredit, "CARD_INACTIVE"));
    }

    // 2. Validar monto
    if (amount <= 0) {
      return Mono.just(createDeclinedResponse(availableCredit, "INVALID_AMOUNT"));
    }

    // 3. Validar crédito disponible
    if (card.getAvailableCredit().doubleValue() < amount) {
      return Mono.just(createDeclinedResponse(availableCredit, "INSUFFICIENT_CREDIT"));
    }

    // 4. Procesar cargo - APPROVED
    double newAvailableCredit = availableCredit - amount;
    card.setAvailableCredit(BigDecimal.valueOf(newAvailableCredit));
    card.setCurrentBalance(card.getCurrentBalance().add(BigDecimal.valueOf(amount)));

    return cardRepository.save(card)
      .map(savedCard -> {
        ChargeAuthorizationResponse response = new ChargeAuthorizationResponse();
        response.setAuthorizationCode(generateAuthCode());
        response.setStatus(ChargeAuthorizationResponse.StatusEnum.APPROVED);
        response.setAuthorizedAmount(amount);
        response.setAvailableCreditAfter(savedCard.getAvailableCredit().doubleValue());
        response.setProcessedAt(OffsetDateTime.now());
        return response;
      })
      .doOnSuccess(response -> log.info("Cargo autorizado: cardId={}, authCode={}, newCredit={}",
        card.getId(), response.getAuthorizationCode(), newAvailableCredit));
  }

  private ChargeAuthorizationResponse createDeclinedResponse(Double availableCredit, String reason) {
    ChargeAuthorizationResponse response = new ChargeAuthorizationResponse();
    response.setAuthorizationCode(null);
    response.setStatus(ChargeAuthorizationResponse.StatusEnum.DECLINED);
    response.setAuthorizedAmount(0.0);
    response.setAvailableCreditAfter(availableCredit);
    response.setProcessedAt(OffsetDateTime.now());
    switch (reason) {
      case "INSUFFICIENT_CREDIT":
        response.setDeclineReason(ChargeAuthorizationResponse.DeclineReasonEnum.INSUFFICIENT_CREDIT);
        break;
      case "CARD_INACTIVE":
        response.setDeclineReason(ChargeAuthorizationResponse.DeclineReasonEnum.CARD_INACTIVE);
        break;
      case "INVALID_AMOUNT":
        response.setDeclineReason(ChargeAuthorizationResponse.DeclineReasonEnum.INVALID_AMOUNT);
        break;
    }
    return response;
  }

  @Override
  public Mono<ChargeAuthorizationResponse> createInvalidAmountResponse() {
    ChargeAuthorizationResponse response = new ChargeAuthorizationResponse();
    response.setAuthorizationCode(null);
    response.setStatus(ChargeAuthorizationResponse.StatusEnum.DECLINED);
    response.setAuthorizedAmount(0.0);
    response.setAvailableCreditAfter(0.0);
    response.setProcessedAt(OffsetDateTime.now());
    response.setDeclineReason(ChargeAuthorizationResponse.DeclineReasonEnum.INVALID_AMOUNT);
    return Mono.just(response);
  }

  private String generateAuthCode() {
    return "AUTH-" + System.currentTimeMillis();
  }

  //Validaciones
  private Mono<Void> validateCreditCreation(String customerId, String customerType) {
    // Validar reglas de negocio según el tipo
    if ("PERSONAL".equals(customerType)) {
      return validatePersonalCreditCardRules(customerId);
    } else {
      return validateEnterpriseCreditCardRules();
    }
  }

  private Mono<Void> validatePersonalCreditCardRules(String customerId) {
    return cardRepository.countByCustomerIdAndIsActiveTrue(customerId)
      .flatMap(activeCredits -> {
        if (activeCredits > 0) {
          return Mono.error(new BusinessRuleException(
            "PERSON_ALREADY_HAS_CREDIT_CARD",
            "Personal customers can only have one active credit card"
          ));
        }
        return Mono.empty();
      });
  }

  private Mono<Void> validateEnterpriseCreditCardRules(/*String customerId*/) {
    // Para empresariales no hay límite
    return Mono.empty();
  }
}

package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerClient;
import com.bootcamp.ntt.card_service.entity.Card;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.mapper.CardMapper;
import com.bootcamp.ntt.card_service.repository.CardRepository;
import com.bootcamp.ntt.card_service.service.CardService;
import com.bootcamp.ntt.cardservice.model.CardCreateRequest;
import com.bootcamp.ntt.cardservice.model.CardResponse;
import com.bootcamp.ntt.cardservice.model.CardUpdateRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

  private final CardRepository cardRepository;
  private final CardMapper cardMapper;
  private final CustomerClient customerClient;

  @Override
  public Flux<CardResponse> getAllCards(Boolean isActive) {
    Flux<Card> cards;
    if (isActive == null) {
      cards = cardRepository.findAll(); // todas
    } else {
      cards = cardRepository.findByIsActive(isActive); // activas o inactivas
    }

    return cards
      .map(cardMapper::toResponse)
      .doOnComplete(() -> log.debug("Credit cards retrieved with filter isActive={}", isActive));
  }

  @Override
  public Mono<CardResponse> getCardById(String id) {
    log.debug("Getting credit card by ID: {}", id);
    return cardRepository.findById(id)
      .map(cardMapper::toResponse)
      .doOnSuccess(credit -> {
        if (credit != null) {
          log.debug("Credit card found with ID: {}", id);
        } else {
          log.debug("Credit card not found with ID: {}", id);
        }
      });
  }

  @Override
  public Mono<CardResponse> createCard(CardCreateRequest cardRequest) {
    log.debug("Creating credit for customer: {}", cardRequest.getCustomerId());

    return customerClient.getCustomerType(cardRequest.getCustomerId())
      .flatMap(customerType -> validateCreditCreation(cardRequest.getCustomerId(), customerType.getType())
        .then(Mono.just(cardRequest))
        .map(request -> cardMapper.toEntity(request, customerType.getType())) // Pasamos el tipo
        .flatMap(cardRepository::save)
        .map(cardMapper::toResponse))
      .doOnSuccess(response -> log.debug("Credit card created with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error creating credit card: {}", error.getMessage()));
  }

  @Override
  public Mono<CardResponse> updateCard(String id, CardUpdateRequest cardRequest) {
    log.debug("Updating credit card with ID: {}", id);

    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .map(existing -> cardMapper.updateEntity(existing, cardRequest))
      .flatMap(cardRepository::save)
      .map(cardMapper::toResponse)
      .doOnSuccess(response -> log.debug("Credit updated with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error updating credit {}: {}", id, error.getMessage()));
  }

  @Override
  public Mono<Void> deleteCard(String id) {
    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .flatMap(cardRepository::delete)
      .doOnSuccess(unused -> log.debug("Credit card deleted"))
      .doOnError(error -> log.error("Error deleting credit card {}: {}", id, error.getMessage()));
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

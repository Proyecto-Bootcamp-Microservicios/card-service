package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerClient;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.mapper.CardMapper;
import com.bootcamp.ntt.card_service.model.CreditReservationRequest;
import com.bootcamp.ntt.card_service.repository.CardRepository;
import com.bootcamp.ntt.card_service.service.CardService;
import com.bootcamp.ntt.card_service.model.CardCreateRequest;
import com.bootcamp.ntt.card_service.model.CardResponse;
import com.bootcamp.ntt.card_service.model.CardUpdateRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;


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
      .doOnComplete(() -> log.debug("Cards active by customer retrieved "));
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
  public Mono<CardResponse> reserveCredit(String id, CreditReservationRequest creditReservationRequest ) {
    log.debug("Applying transaction of {} to card {}", creditReservationRequest.getAmount(), id);

    BigDecimal amount = BigDecimal.valueOf(creditReservationRequest.getAmount());

    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        if (card.getAvailableCredit().compareTo(amount) < 0) {
          return Mono.error(new BusinessRuleException(
            "INSUFFICIENT_CREDIT",
            "Transaction amount exceeds available credit"
          ));
        }

        // Actualizar campos
        card.setAvailableCredit(card.getAvailableCredit().subtract(amount));
        card.setCurrentBalance(card.getCurrentBalance().add(amount));

        return cardRepository.save(card);
      })
      .map(cardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Transaction applied successfully to card {}", id))
      .doOnError(e -> log.error("Error applying transaction to card {}: {}", id, e.getMessage()));
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

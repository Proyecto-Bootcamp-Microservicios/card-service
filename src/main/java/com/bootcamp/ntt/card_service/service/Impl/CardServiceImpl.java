package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerClient;
import com.bootcamp.ntt.card_service.entity.Card;
import com.bootcamp.ntt.card_service.entity.DailyBalance;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.mapper.CreditCardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.repository.CardRepository;
import com.bootcamp.ntt.card_service.repository.DailyBalanceRepository;
import com.bootcamp.ntt.card_service.service.CardService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;


@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

  private final CardRepository cardRepository;
  private final DailyBalanceRepository dailyBalanceRepository;
  private final CreditCardMapper creditCardMapper;
  private final CustomerClient customerClient;
  private static final SecureRandom random = new SecureRandom();


  @Override
  public Flux<CardResponse> getAllCards(Boolean isActive) {
    return cardRepository.findAll()
      .map(creditCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards retrieved"));
  }

  @Override
  public Flux<CardResponse> getCardsByActive(Boolean isActive) {
    return cardRepository.findByIsActive(isActive)
      .map(creditCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Active cards retrieved"));
  }

  @Override
  public Flux<CardResponse> getCardsByActiveAndCustomer(Boolean isActive, String customerId) {
    return cardRepository.findByIsActiveAndCustomerId(isActive, customerId)
      .map(creditCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards active by customer retrieved"));
  }

  @Override
  public Mono<CardResponse> getCardById(String id) {
    log.debug("Getting credit card by ID: {}", id);
    return cardRepository.findById(id)
      .map(creditCardMapper::toResponse)
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
    log.debug("Creating card for customer: {}", cardRequest.getCustomerId());

    return customerClient.getCustomerType(cardRequest.getCustomerId())
      .flatMap(customerType -> validateCreditCreation(cardRequest.getCustomerId(), customerType.getCustomerType())
        //.then(Mono.just(cardRequest))
        .then(generateUniqueCardNumber())
        .map(cardNumber -> creditCardMapper.toEntity(cardRequest, customerType.getCustomerType(),cardNumber)) // Pasamos el tipo
        .flatMap(cardRepository::save)
        .map(creditCardMapper::toResponse))
      .doOnSuccess(response -> log.debug("Card created with ID: {}", response.getId()))
      .doOnError(error -> log.error("Error creating card: {}", error.getMessage()));
  }

  @Override
  public Mono<CardResponse> updateCard(String id, CardUpdateRequest cardRequest) {
    log.debug("Updating card with ID: {}", id);

    return cardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .map(existing -> creditCardMapper.updateEntity(existing, cardRequest))
      .flatMap(cardRepository::save)
      .map(creditCardMapper::toResponse)
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
      .map(creditCardMapper::toResponse)
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
      .map(creditCardMapper::toResponse)
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
    }
    return response;
  }

  private String generateAuthCode() {
    return "AUTH-" + System.currentTimeMillis();
  }


  public Mono<String> generateUniqueCardNumber() {
    String candidate = generateRandomCardNumber();

    return cardRepository.findByCardNumber(candidate)
      .flatMap(existing -> generateUniqueCardNumber()) // si existe, intenta de nuevo
      .switchIfEmpty(Mono.just(candidate)); // si no existe, úsalo
  }

  private String generateRandomCardNumber() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      sb.append(random.nextInt(10));
    }
    return sb.toString();
  }

  @Override
  public Mono<PaymentProcessResponse> processPayment(String cardNumber, PaymentProcessRequest paymentRequest) {
    log.debug("Processing payment for card: {}, amount: {}", cardNumber, paymentRequest.getAmount());
    return cardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + cardNumber)))
      .flatMap(card -> validateAndProcessPayment(card, paymentRequest))
      .doOnSuccess(response -> {
        if (response.getSuccess()) {
          log.info("Payment processed successfully for card {}: paid {}",
            cardNumber, response.getActualPaymentAmount());
        } else {
          log.warn("Payment failed for card {}: {}", cardNumber, response.getErrorMessage());
        }
      })
      .doOnError(error -> log.error("Error processing payment for card {}: {}", cardNumber, error.getMessage()));
  }
  @Override
  public Mono<CardBalanceResponse> getCardBalance(String cardNumber) {
    log.debug("Getting balance for card: {}", cardNumber);

    return cardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + cardNumber)))
      .map(this::buildBalanceResponse)
      .doOnSuccess(response -> log.debug("Balance retrieved for card: {}", cardNumber))
      .doOnError(error -> log.error("Error getting balance for card {}: {}", cardNumber, error.getMessage()));
  }

  @Override
  public Mono<CustomerCardValidationResponse> getCustomerCardValidation(String customerId) {
    log.debug("Validating customer cards for customer: {}", customerId);

    return cardRepository.findByIsActiveAndCustomerId(true, customerId)
      .collectList()
      .map(activeCards -> buildCustomerValidationResponse(customerId, activeCards))
      .doOnSuccess(response -> log.debug("Customer validation completed for {}: hasActiveCard={}",
        customerId, response.getHasActiveCard()))
      .doOnError(error -> log.error("Error validating customer {}: {}", customerId, error.getMessage()));
  }

  // Método para capturar todos los saldos diarios (job programado)
  @Override
  public Mono<Void> captureAllDailyBalances() {
    LocalDate today = LocalDate.now();
    log.info("Starting daily balance capture for date: {}", today);

    return cardRepository.findByIsActive(true)
      .flatMap(card -> captureCardBalanceForDate(card, today))
      .then()
      .doOnSuccess(v -> log.info("Daily balance capture completed for date: {}", today))
      .doOnError(error -> log.error("Error during daily balance capture: {}", error.getMessage()));
  }

  // Método helper para capturar saldo de una tarjeta
  private Mono<Void> captureCardBalanceForDate(Card card, LocalDate date) {
    return dailyBalanceRepository.existsByCardIdAndDate(card.getId(), date)
      .flatMap(exists -> {
        if (exists) {
          log.debug("Balance already captured for card {} on date {}", card.getId(), date);
          return Mono.<Void>empty();
        }

        DailyBalance dailyBalance = new DailyBalance();
        dailyBalance.setCustomerId(card.getCustomerId());
        dailyBalance.setCardId(card.getId());
        dailyBalance.setCardNumber(card.getCardNumber());
        dailyBalance.setDate(date);
        dailyBalance.setCurrentBalance(card.getCurrentBalance());
        dailyBalance.setAvailableCredit(card.getAvailableCredit());
        dailyBalance.setCreditLimit(card.getCreditLimit());
        dailyBalance.setCapturedAt(LocalDateTime.now());

        return dailyBalanceRepository.save(dailyBalance).then();
      });
  }

  // Método para obtener promedios diarios (para report-service)
  @Override
  public Mono<CustomerDailyAverageResponse> getCustomerDailyAverages(String customerId, Integer year, Integer month) {
    log.debug("Getting daily averages for customer: {} for {}/{}", customerId, month, year);

    LocalDate startDate = LocalDate.of(year, month, 1);
    LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

    return dailyBalanceRepository.findByCustomerIdAndDateBetween(customerId, startDate, endDate)
      .groupBy(DailyBalance::getCardId)
      .flatMap(this::calculateCardAverage)
      .collectList()
      .map(products -> buildDailyAverageResponse(customerId, year, month, products))
      .doOnSuccess(response -> log.debug("Daily averages calculated for customer {}: {} products",
        customerId, response.getProducts().size()))
      .doOnError(error -> log.error("Error calculating daily averages for customer {}: {}", customerId, error.getMessage()));
  }

  // Helper para calcular promedio de una tarjeta
  private Mono<CustomerDailyAverageResponseProductsInner> calculateCardAverage(Flux<DailyBalance> cardBalances) {
    return cardBalances.collectList()
      .mapNotNull(balances -> {
        if (balances.isEmpty()) return null;

        DailyBalance firstBalance = balances.get(0);
        BigDecimal sumBalance = balances.stream()
          .map(DailyBalance::getCurrentBalance)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumAvailable = balances.stream()
          .map(DailyBalance::getAvailableCredit)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

        double averageBalance = sumBalance.divide(BigDecimal.valueOf(balances.size()), 2, java.math.RoundingMode.HALF_UP).doubleValue();
        double averageAvailable = sumAvailable.divide(BigDecimal.valueOf(balances.size()), 2, java.math.RoundingMode.HALF_UP).doubleValue();

        CustomerDailyAverageResponseProductsInner product = new CustomerDailyAverageResponseProductsInner();
        product.setCardId(firstBalance.getCardId());
        product.setCardNumber(firstBalance.getCardNumber());
        product.setProductType(CustomerDailyAverageResponseProductsInner.ProductTypeEnum.CREDIT_CARD);
        product.setAverageDailyBalance(averageBalance);
        product.setAverageDailyAvailable(averageAvailable);
        product.setTotalDaysWithData(balances.size());
        product.setIsComplete(balances.size() >= LocalDate.now().lengthOfMonth());

        return product;
      })
      .filter(Objects::nonNull);
  }

  // Helper para construir respuesta final
  private CustomerDailyAverageResponse buildDailyAverageResponse(String customerId, Integer year, Integer month,
                                                                 java.util.List<CustomerDailyAverageResponseProductsInner> products) {
    CustomerDailyAverageResponse response = new CustomerDailyAverageResponse();
    response.setCustomerId(customerId);

    CustomerDailyAverageResponsePeriod period = new CustomerDailyAverageResponsePeriod();
    period.setYear(year);
    period.setMonth(month);
    period.setMonthName(java.time.Month.of(month).name());
    response.setPeriod(period);

    response.setProducts(products);
    response.setGeneratedAt(java.time.OffsetDateTime.now());

    return response;
  }

  //  helper para la respuesta
  private CustomerCardValidationResponse buildCustomerValidationResponse(String customerId, List<Card> activeCards) {
    CustomerCardValidationResponse response = new CustomerCardValidationResponse();
    response.setCustomerId(customerId);
    response.setHasActiveCard(!activeCards.isEmpty());
    response.setActiveCardCount(activeCards.size());
    response.setValidatedAt(OffsetDateTime.now());

    // Si hay tarjetas activas, agregar el resumen
    if (!activeCards.isEmpty()) {
      java.util.List<CustomerCardValidationResponseCardSummaryInner> cardSummary =
        activeCards.stream()
          .map(this::buildCardSummary)
          .collect(java.util.stream.Collectors.toList());
      response.setCardSummary(cardSummary);
    }

    return response;
  }

  //  helper para el resumen de cada tarjeta
  private CustomerCardValidationResponseCardSummaryInner buildCardSummary(Card card) {
    CustomerCardValidationResponseCardSummaryInner summary = new CustomerCardValidationResponseCardSummaryInner();
    summary.setCardId(card.getId());
    summary.setCardNumber(card.getCardNumber());
    summary.setType(CustomerCardValidationResponseCardSummaryInner.TypeEnum.valueOf(card.getType().name()));
    summary.setCreditLimit(card.getCreditLimit().doubleValue());
    summary.setAvailableCredit(card.getAvailableCredit().doubleValue());
    return summary;
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

  private Mono<PaymentProcessResponse> validateAndProcessPayment(Card card, PaymentProcessRequest request) {
    BigDecimal paymentAmount = BigDecimal.valueOf(request.getAmount());
    // Validar estado de la tarjeta
    if (!card.isActive()) {
      return Mono.just(createPaymentFailedResponse(card.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.CARD_INACTIVE, "Card is not active"));
    }

    // Validar monto
    if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return Mono.just(createPaymentFailedResponse(card.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.INVALID_AMOUNT, "Payment amount must be greater than 0"));
    }

    //  Validar balance cero
    if (card.getCurrentBalance().compareTo(BigDecimal.ZERO) == 0) {
      return Mono.just(createPaymentFailedResponse(card.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.ZERO_CURRENT_BALANCE, "Card has no outstanding balance"));
    }

    // Determinar monto real a pagar
    BigDecimal actualPaymentAmount = calculateActualPaymentAmount(card, paymentAmount);

    // Calcular nuevos saldos
    BigDecimal newCurrentBalance = card.getCurrentBalance().subtract(actualPaymentAmount);
    BigDecimal newAvailableCredit = card.getAvailableCredit().add(actualPaymentAmount);

    // Actualizar la tarjeta
    card.setCurrentBalance(newCurrentBalance);
    card.setAvailableCredit(newAvailableCredit);

    return cardRepository.save(card)
      .map(savedCard -> createPaymentSuccessResponse(savedCard, paymentAmount, actualPaymentAmount));
  }
  private BigDecimal calculateActualPaymentAmount(Card card, BigDecimal requestedAmount) {
    // Si el pago es mayor al balance, se paga solo lo que se debe
    if (requestedAmount.compareTo(card.getCurrentBalance()) > 0) {
      log.info("Payment amount {} exceeds balance {}, adjusting to full balance",
        requestedAmount, card.getCurrentBalance());
      return card.getCurrentBalance();
    }
    return requestedAmount;
  }
  private PaymentProcessResponse createPaymentSuccessResponse(Card card, BigDecimal requestedAmount, BigDecimal actualAmount) {
    PaymentProcessResponse response = new PaymentProcessResponse();
    response.setSuccess(true);
    response.setCardId(card.getId());
    response.setRequestedAmount(requestedAmount.doubleValue());
    response.setActualPaymentAmount(actualAmount.doubleValue());
    response.setAvailableCreditAfter(card.getAvailableCredit().doubleValue());
    response.setCurrentBalanceAfter(card.getCurrentBalance().doubleValue());
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }
  private PaymentProcessResponse createPaymentFailedResponse(String cardId, BigDecimal requestedAmount,
                                                             PaymentProcessResponse.ErrorCodeEnum errorCode, String errorMessage) {
    PaymentProcessResponse response = new PaymentProcessResponse();
    response.setSuccess(false);
    response.setCardId(cardId);
    response.setRequestedAmount(requestedAmount.doubleValue());
    response.setErrorCode(errorCode);
    response.setErrorMessage(errorMessage);
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }
  private CardBalanceResponse buildBalanceResponse(Card card) {
    // Calcular porcentaje de utilización
    BigDecimal utilizationPercentage = card.getCreditLimit().compareTo(BigDecimal.ZERO) > 0
      ? card.getCurrentBalance()
      .multiply(BigDecimal.valueOf(100))
      .divide(card.getCreditLimit(), 2, java.math.RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    CardBalanceResponse response = new CardBalanceResponse();
    response.setCardId(card.getId());
    response.setCardNumber(card.getCardNumber());
    response.setCreditLimit(card.getCreditLimit().doubleValue());
    response.setAvailableCredit(card.getAvailableCredit().doubleValue());
    response.setCurrentBalance(card.getCurrentBalance().doubleValue());
    response.setUtilizationPercentage(utilizationPercentage.doubleValue());
    response.setIsActive(card.isActive());
    return response;
  }
}

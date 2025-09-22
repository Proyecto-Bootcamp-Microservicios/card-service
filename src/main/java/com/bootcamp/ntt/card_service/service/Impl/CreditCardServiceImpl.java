package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerServiceClient;
import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.entity.DailyBalance;
import com.bootcamp.ntt.card_service.enums.CardStatus;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.exception.TransactionServiceUnavailableException;
import com.bootcamp.ntt.card_service.mapper.CreditCardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.repository.CreditCardRepository;
import com.bootcamp.ntt.card_service.repository.DailyBalanceRepository;
import com.bootcamp.ntt.card_service.service.CreditCardService;
import com.bootcamp.ntt.card_service.service.ExternalServiceWrapper;
import com.bootcamp.ntt.card_service.utils.CardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static com.bootcamp.ntt.card_service.utils.CacheKeys.BALANCE_TTL;
import static com.bootcamp.ntt.card_service.utils.CacheKeys.MASTER_DATA_TTL;


@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCardServiceImpl implements CreditCardService {

  private final CreditCardRepository creditCardRepository;
  private final DailyBalanceRepository dailyBalanceRepository;
  private final CreditCardMapper creditCardMapper;
  private final CustomerServiceClient customerServiceClient;
  private final TransactionServiceClient transactionServiceClient;
  private final ExternalServiceWrapper externalServiceWrapper;
  private final CardUtils cardUtils;
  private final ReactiveRedisTemplate<String, Object> redisTemplate;


  @Override
  public Flux<CreditCardResponse> getCardsByActive(Boolean isActive) {
    return creditCardRepository.findByIsActiveAndType(isActive, CardType.CREDIT)
      .map(creditCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Active cards retrieved"));
  }

  @Override
  public Flux<CreditCardResponse> getCardsByActiveAndCustomer(Boolean isActive, String customerId) {
    return creditCardRepository.findByIsActiveAndCustomerIdAndType(isActive, customerId, CardType.CREDIT)
      .map(creditCardMapper::toResponse)
      .doOnComplete(() -> log.debug("Cards active by customer retrieved"));
  }

  @Override
  public Mono<CreditCardResponse> getCardById(String id) {
    String cacheKey = "card:master:id:" + id;
    log.debug("Getting credit card by ID: {}", id);

    return getCachedValue(cacheKey, CreditCardResponse.class)
      .switchIfEmpty(
        creditCardRepository.findById(id)
          .map(creditCardMapper::toResponse)
          .flatMap(response ->
            setCachedValue(cacheKey, response, MASTER_DATA_TTL)
              .thenReturn(response)
          )
          .doOnSuccess(card -> {
            if (card != null) {
              log.debug("Card found and cached: {}", id);
            } else {
              log.debug("Card not found: {}", id);
            }
          })
      );
  }

  @Override
  public Mono<CreditCardResponse> getCardByCardNumber(String cardNumber) {
    String cacheKey = "card:master:number:" + cardNumber;
    log.debug("Getting credit card by cardNumber: {}", cardNumber);

    return getCachedValue(cacheKey, CreditCardResponse.class)
      .switchIfEmpty(
        creditCardRepository.findByCardNumber(cardNumber)
          .map(creditCardMapper::toResponse)
          .flatMap(response ->
            setCachedValue(cacheKey, response, MASTER_DATA_TTL)
              .thenReturn(response)
          )
          .doOnSuccess(card -> {
            if (card != null) {
              log.debug("Card found and cached: {}", cardNumber);
            } else {
              log.debug("Card not found: {}", cardNumber);
            }
          })
      );
  }

  @Override
  public Mono<CreditCardResponse> createCard(CreditCardCreateRequest cardRequest) {
    log.debug("Creating card for customer: {}", cardRequest.getCustomerId());

    return externalServiceWrapper.getCustomerTypeWithCircuitBreaker(cardRequest.getCustomerId())
      .flatMap(customerType -> validateCreditCreation(cardRequest.getCustomerId(), customerType.getCustomerType())
        .then(generateUniqueCardNumber())
        .map(cardNumber -> creditCardMapper.toEntity(cardRequest, customerType.getCustomerType(), cardNumber))
        .flatMap(creditCardRepository::save)
        .map(creditCardMapper::toResponse))
      .doOnSuccess(response -> {
        log.debug("Card created with ID: {}", response.getId());
        // ✅ INVALIDAR CACHE DEL CLIENTE
        invalidateCustomerCaches(response.getCustomerId());
      });
  }

  @Override
  public Mono<CreditCardResponse> updateCard(String id, CreditCardUpdateRequest cardRequest) {
    log.debug("Updating card with ID: {}", id);

    return creditCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .map(existing -> creditCardMapper.updateEntity(existing, cardRequest))
      .flatMap(creditCardRepository::save)
      .map(card -> {
        invalidateCardCaches(card.getId(), card.getCardNumber(), card.getCustomerId());
        return creditCardMapper.toResponse(card);
      })
      .doOnSuccess(response -> log.debug("Card updated with ID: {}", response.getId()));
  }

  @Override
  public Mono<Void> deleteCard(String id) {
    return creditCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Credit card not found")))
      .flatMap(card -> {
        String customerId = card.getCustomerId();
        String cardNumber = card.getCardNumber();

        return creditCardRepository.delete(card)
          .doOnSuccess(unused -> {
            log.debug("Card deleted");
            invalidateCardCaches(id, cardNumber, customerId);
          });
      });
  }

  @Override
  public Mono<CreditCardResponse> deactivateCard(String id) {
    return creditCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(false);  // soft delete
        return creditCardRepository.save(card);
      })
      .map(creditCardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Card {} deactivated", id));
  }

  @Override
  public Mono<CreditCardResponse> activateCard(String id) {
    return creditCardRepository.findById(id)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + id)))
      .flatMap(card -> {
        card.setActive(true);  // reactivar
        return creditCardRepository.save(card);
      })
      .map(creditCardMapper::toResponse)
      .doOnSuccess(c -> log.debug("Card {} activated", id));
  }

  //helpers
  private <T> Mono<T> getCachedValue(String key, Class<T> valueType) {
    return redisTemplate.opsForValue()
      .get(key)
      .cast(valueType)
      .doOnNext(cached -> log.debug("REDIS CACHE HIT: {}", key))
      .onErrorResume(error -> {
        log.warn("Redis read error for key {}: {}", key, error.getMessage());
        return Mono.empty();
      });
  }

  private Mono<Boolean> setCachedValue(String key, Object value, Duration ttl) {
    return redisTemplate.opsForValue()
      .set(key, value, ttl)
      .doOnSuccess(success -> {
        if (success) {
          log.debug("REDIS CACHE SET: {} (TTL: {})", key, ttl);
        } else {
          log.warn("Redis cache SET failed for key: {}", key);
        }
      })
      .onErrorResume(error -> {
        log.error("❌ Redis write error for key {}: {}", key, error.getMessage());
        return Mono.just(false);
      });
  }

  //invalidaciones
  private void invalidateCardCaches(String cardId, String cardNumber, String customerId) {
    Flux.just(
        "card:master:id:" + cardId,
        "card:master:number:" + cardNumber,
        "card:balance:" + cardNumber,
        "card:eligibility:" + customerId
      )
      .flatMap(redisTemplate::delete)
      .doOnNext(deleted -> log.debug("✅ Card cache invalidated: {}", deleted))
      .subscribe();
  }

  private void invalidateCustomerCaches(String customerId) {
    Flux.just(
        "card:eligibility:" + customerId
      )
      .flatMap(redisTemplate::delete)
      .doOnNext(deleted -> log.debug("✅ Customer cache invalidated: {}", deleted))
      .subscribe();
  }

  @Override
  public Mono<ChargeAuthorizationResponse> authorizeCharge(String cardNumber, ChargeAuthorizationRequest request) {
    return creditCardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + cardNumber)))
      .flatMap(creditCard -> validateAndProcessCharge(creditCard, request));
  }

  private Mono<ChargeAuthorizationResponse> validateAndProcessCharge(CreditCard card, ChargeAuthorizationRequest request) {
    Double availableCredit = card.getAvailableCredit().doubleValue();

    if (!card.isActive()) {
      return Mono.just(creditCardMapper.toChargeDeclinedResponse(availableCredit, "CARD_INACTIVE"));
    }

    if (request.getAmount() <= 0) {
      return Mono.just(creditCardMapper.toChargeDeclinedResponse(availableCredit, "INVALID_AMOUNT"));
    }

    if (card.getAvailableCredit().doubleValue() < request.getAmount()) {
      return Mono.just(creditCardMapper.toChargeDeclinedResponse(availableCredit, "INSUFFICIENT_CREDIT"));
    }

    return processApprovedCharge(card, request);
  }

  private Mono<ChargeAuthorizationResponse> processApprovedCharge(CreditCard card, ChargeAuthorizationRequest request) {
    BigDecimal chargeAmount = BigDecimal.valueOf(request.getAmount());
    BigDecimal newAvailableCredit = card.getAvailableCredit().subtract(chargeAmount);
    BigDecimal newCurrentBalance = card.getCurrentBalance().add(chargeAmount);
    String authCode = cardUtils.generateAuthCode();

    BigDecimal originalAvailableCredit = card.getAvailableCredit();
    BigDecimal originalCurrentBalance = card.getCurrentBalance();
    CardStatus originalStatus = card.getStatus();

    card.setStatus(CardStatus.CHARGE_PENDING);

    return creditCardRepository.save(card)
      .flatMap(savedCard -> {
        // Crear la transacción
        TransactionRequest transactionRequest = creditCardMapper.toTransactionRequest(
          savedCard, request, authCode);

        return externalServiceWrapper.createTransactionWithCircuitBreaker(transactionRequest)
          .then(Mono.defer(() -> {
            savedCard.setAvailableCredit(newAvailableCredit);
            savedCard.setCurrentBalance(newCurrentBalance);
            savedCard.setStatus(CardStatus.ACTIVE);

            return creditCardRepository.save(savedCard)
              .map(finalCard -> creditCardMapper.toChargeApprovedResponse(
                finalCard, request.getAmount(), authCode));
          }))
          .onErrorResume(error -> {
            savedCard.setAvailableCredit(originalAvailableCredit);
            savedCard.setCurrentBalance(originalCurrentBalance);
            savedCard.setStatus(originalStatus != null ? originalStatus : CardStatus.ACTIVE);

            return creditCardRepository.save(savedCard)
              .then(Mono.error(new TransactionServiceUnavailableException(
                "Transaction service failed. Charge authorization reverted.")));
          });
      });
  }

  public Mono<String> generateUniqueCardNumber() {
    String candidate = cardUtils.generateRandomCardNumber();
    return creditCardRepository.findByCardNumber(candidate)
      .flatMap(existing -> generateUniqueCardNumber())
      .switchIfEmpty(Mono.just(candidate));
  }

  @Override
  public Mono<PaymentProcessResponse> processPayment(String cardNumber, PaymentProcessRequest paymentRequest) {
    log.debug("Processing payment for card: {}, amount: {}", cardNumber, paymentRequest.getAmount());
    return creditCardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found with id: " + cardNumber)))
      .flatMap(card -> validateAndProcessPayment(card, paymentRequest))
      .doOnSuccess(response -> {
        if (response.getSuccess()) {
          log.info("Payment processed successfully for card {}: paid {}",
            cardNumber, response.getActualPaymentAmount());
        } else {
          log.warn("Payment failed for card {}: {}", cardNumber, response.getErrorMessage());
        }
      });
  }

  @Override
  public Mono<CreditCardBalanceResponse> getCardBalance(String cardNumber) {
    return creditCardRepository.findByCardNumber(cardNumber)
      .switchIfEmpty(Mono.error(new RuntimeException("Card not found: " + cardNumber)))
      .map(creditCardMapper::toBalanceResponse)
      .doOnSuccess(response -> log.debug("Balance retrieved fresh from DB: {}", cardNumber));
  }

  @Override
  public Mono<CustomerCardValidationResponse> getCustomerCardValidation(String customerId) {
    log.debug("Validating customer cards for customer: {}", customerId);

    return creditCardRepository.findByIsActiveAndCustomerIdAndType(true, customerId, CardType.CREDIT)
      .collectList()
      .map(activeCards -> creditCardMapper.toCustomerValidationResponse(customerId, activeCards))
      .doOnSuccess(response -> log.debug("Customer validation completed for {}: hasActiveCard={}",
        customerId, response.getHasActiveCard()));
  }

  // Método para capturar todos los saldos diarios (job programado)
  @Override
  public Mono<Void> captureAllDailyBalances() {
    LocalDate today = LocalDate.now();
    log.info("Starting daily balance capture for date: {}", today);

    return creditCardRepository.findByIsActiveAndType(true, CardType.CREDIT)
      .flatMap(card -> captureCardBalanceForDate(card, today))
      .then()
      .doOnSuccess(v -> log.info("Daily balance capture completed for date: {}", today));
  }

  //  helper para capturar saldo diario de una tarjeta
  private Mono<Void> captureCardBalanceForDate(CreditCard card, LocalDate date) {
    return dailyBalanceRepository.existsByCardIdAndDate(card.getId(), date)
      .flatMap(exists -> {
        if (exists) {
          log.debug("Balance already captured for card {} on date {}", card.getId(), date);
          return Mono.empty();
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
      .map(products -> creditCardMapper.toDailyAverageResponse(customerId, year, month, products))
      .doOnSuccess(response -> log.debug("Daily averages calculated for customer {}: {} products",
        customerId, response.getProducts().size()));
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

  public Mono<ProductEligibilityResponse> checkCustomerProductEligibility(String customerId) {
    log.debug("Checking product eligibility for customer: {}", customerId);

    /*return externalServiceWrapper.getCustomerWithCircuitBreaker(customerId)
      .flatMap(customer -> getCustomerEligibilityStatus(customerId))
      .doOnSuccess(response -> log.debug("Eligibility checked for customer: {} - Eligible: {}",
        customerId, response.getIsEligible()))
      .doOnError(error -> log.error("Error checking eligibility for customer {}: {}",
        customerId, error.getMessage()));*/
    // Para pruebas locales, omite la llamada externa:
    return getCustomerEligibilityStatus(customerId)
      .doOnSuccess(response -> log.debug("Eligibility checked for customer: {} - Eligible: {}",
        customerId, response.getIsEligible()));
  }

  private Mono<ProductEligibilityResponse> getCustomerEligibilityStatus(String customerId) {
    return getOverdueCreditProducts(customerId)
      .collectList()
      .map(overdueProducts -> creditCardMapper.toProductEligibilityResponse(customerId, overdueProducts));
  }

  private Flux<OverdueProduct> getOverdueCreditProducts(String customerId) {
    log.debug("Checking overdue credit products for customer: {}", customerId);

    return creditCardRepository.findByCustomerId(customerId)
      .filter(creditCard -> Boolean.TRUE.equals(creditCard.getIsOverdue()))
      .map(creditCardMapper::toOverdueProduct)
      .doOnNext(overdueProduct -> log.debug("Found overdue product: {} for customer: {}",
        overdueProduct.getProductId(), customerId));
  }



  // Método auxiliar que puedes llamar periódicamente o al consultar
  public Mono<Void> updateOverdueStatus(String creditCardId) {
    return creditCardRepository.findById(creditCardId)
      .flatMap(creditCard -> {
        if (creditCard.getPaymentDueDate() != null) {
          LocalDate today = LocalDate.now();
          boolean isOverdue = today.isAfter(creditCard.getPaymentDueDate());
          int overdueDays = isOverdue ?
            (int) creditCard.getPaymentDueDate().until(today, ChronoUnit.DAYS) : 0;

          creditCard.setIsOverdue(isOverdue);
          creditCard.setOverdueDays(overdueDays);

          return creditCardRepository.save(creditCard);
        }
        return Mono.just(creditCard);
      })
      .then();
  }

  //Validaciones
  private Mono<Void> validateCreditCreation(String customerId, String customerType) {
    if ("PERSONAL".equals(customerType)) {
      return validatePersonalCreditCardRules(customerId);
    } else {
      return validateEnterpriseCreditCardRules();
    }
  }

  private Mono<Void> validatePersonalCreditCardRules(String customerId) {
    return creditCardRepository.countByCustomerIdAndIsActiveTrue(customerId)
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

  private Mono<PaymentProcessResponse> validateAndProcessPayment(CreditCard card, PaymentProcessRequest request) {
    BigDecimal paymentAmount = BigDecimal.valueOf(request.getAmount());
    // Validar estado de la tarjeta
    if (!card.isActive()) {
      return Mono.just(creditCardMapper.toPaymentFailedResponse(card.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.CARD_INACTIVE, "Card is not active"));
    }

    // Validar monto
    if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return Mono.just(creditCardMapper.toPaymentFailedResponse(card.getId(), paymentAmount,
        PaymentProcessResponse.ErrorCodeEnum.INVALID_AMOUNT, "Payment amount must be greater than 0"));
    }

    //  Validar balance cero
    if (card.getCurrentBalance().compareTo(BigDecimal.ZERO) == 0) {
      return Mono.just(creditCardMapper.toPaymentFailedResponse(card.getId(), paymentAmount,
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

    return creditCardRepository.save(card)
      .map(savedCard -> creditCardMapper.toPaymentSuccessResponse(savedCard, paymentAmount, actualPaymentAmount));
  }

  private BigDecimal calculateActualPaymentAmount(CreditCard card, BigDecimal requestedAmount) {
    // Si el pago es mayor al balance, se paga solo lo que se debe
    if (requestedAmount.compareTo(card.getCurrentBalance()) > 0) {
      log.info("Payment amount {} exceeds balance {}, adjusting to full balance",
        requestedAmount, card.getCurrentBalance());
      return card.getCurrentBalance();
    }
    return requestedAmount;
  }

  @Override
  public Mono<Integer> getActiveCardsCount() {
    log.debug("Getting total count of active credit cards");

    return creditCardRepository.countByIsActiveAndType(true, CardType.CREDIT)
      .map(Long::intValue)
      .doOnSuccess(count -> log.debug("Found {} active credit cards", count));
  }
}

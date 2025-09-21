package com.bootcamp.ntt.card_service.mapper;

import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.enums.CreditCardType;
import com.bootcamp.ntt.card_service.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreditCardMapper {

  private static final int PERCENTAGE_MULTIPLIER = 100;

  public CreditCard toEntity(CreditCardCreateRequest dto, String customerType, String cardNumber) {
    if (dto == null) {
      return null;
    }

    CreditCard card = new CreditCard();
    card.setCardNumber(cardNumber);
    card.setType(CardType.CREDIT);
    card.setCustomerId(dto.getCustomerId());
    card.setCreditCardType(CreditCardType.valueOf(customerType));
    card.setCreditLimit(BigDecimal.valueOf(dto.getCreditLimit()));
    card.setAvailableCredit(
      dto.getAvailableCredit() != null ? BigDecimal.valueOf(dto.getAvailableCredit()) : BigDecimal.ZERO
    );
    card.setCurrentBalance(
      dto.getCurrentBalance() != null ? BigDecimal.valueOf(dto.getCurrentBalance()) : BigDecimal.ZERO
    );
    card.setPaymentDueDate(null);
    card.setMinimumPayment(BigDecimal.valueOf(500));
    card.setIsOverdue(false);
    card.setOverdueDays(0);
    card.setActive(true);
    return card;
  }

  public CreditCard updateEntity(CreditCard existing, CreditCardUpdateRequest dto) {
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

  public CreditCardResponse toResponse(CreditCard entity) {
    if (entity == null) {
      return null;
    }

    CreditCardResponse response = new CreditCardResponse();
    response.setId(entity.getId());
    response.setCardNumber(entity.getCardNumber());
    response.setCustomerId(entity.getCustomerId());
    response.setCreditCardType(CreditCardResponse.CreditCardTypeEnum.valueOf(entity.getCreditCardType().name()));
    response.setCreditLimit(entity.getCreditLimit().doubleValue());
    response.setAvailableCredit(entity.getAvailableCredit().doubleValue());
    response.setCurrentBalance(entity.getCurrentBalance().doubleValue());
    response.setIsActive(entity.isActive());
    response.setPaymentDueDate(entity.getPaymentDueDate());
    response.setMinimumPayment(entity.getMinimumPayment() != null ?
      entity.getMinimumPayment().doubleValue() : 0.0);
    response.setIsOverdue(entity.getIsOverdue() != null ?
      entity.getIsOverdue() : false);
    response.setOverdueDays(entity.getOverdueDays() != null ?
      entity.getOverdueDays() : 0);
    response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null);
    response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

    return response;
  }

  public CreditCardBalanceResponse toBalanceResponse(CreditCard entity) {
    if (entity == null) {
      return null;
    }

    CreditCardBalanceResponse response = new CreditCardBalanceResponse();
    response.setCardId(entity.getId());
    response.setCardNumber(entity.getCardNumber());
    response.setCreditLimit(entity.getCreditLimit().doubleValue());
    response.setAvailableCredit(entity.getAvailableCredit().doubleValue());
    response.setCurrentBalance(entity.getCurrentBalance().doubleValue());

    // Calcular porcentaje de utilización
    BigDecimal utilizationPercentage = entity.getCreditLimit().compareTo(BigDecimal.ZERO) > 0
      ? entity.getCurrentBalance().multiply(BigDecimal.valueOf(PERCENTAGE_MULTIPLIER))
      .divide(entity.getCreditLimit(), 2, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    response.setUtilizationPercentage(utilizationPercentage.doubleValue());

    response.setIsActive(entity.isActive());
    return response;
  }

  public CustomerCardValidationResponseCardSummaryInner toCardSummary(CreditCard entity) {
    if (entity == null) {
      return null;
    }

    CustomerCardValidationResponseCardSummaryInner summary = new CustomerCardValidationResponseCardSummaryInner();
    summary.setCardId(entity.getId());
    summary.setCardNumber(entity.getCardNumber());
    summary.setType(CustomerCardValidationResponseCardSummaryInner.TypeEnum.valueOf(entity.getType().name()));
    summary.setCreditLimit(entity.getCreditLimit().doubleValue());
    summary.setAvailableCredit(entity.getAvailableCredit().doubleValue());
    return summary;
  }

  public OverdueProduct toOverdueProduct(CreditCard entity) {
    if (entity == null) {
      return null;
    }

    OverdueProduct overdueProduct = new OverdueProduct();
    overdueProduct.setProductId(entity.getId());
    overdueProduct.setProductType(OverdueProduct.ProductTypeEnum.CREDIT_CARD);
    overdueProduct.setOverdueAmount(entity.getMinimumPayment() != null
      ? entity.getMinimumPayment().doubleValue() : entity.getCurrentBalance().doubleValue());
    overdueProduct.setDaysPastDue(entity.getOverdueDays() != null ? entity.getOverdueDays() : 0);
    return overdueProduct;
  }

  public ChargeAuthorizationResponse toChargeApprovedResponse(CreditCard card, Double amount, String authCode) {
    ChargeAuthorizationResponse response = new ChargeAuthorizationResponse();
    response.setAuthorizationCode(authCode);
    response.setStatus(ChargeAuthorizationResponse.StatusEnum.APPROVED);
    response.setAuthorizedAmount(amount);
    response.setAvailableCreditAfter(card.getAvailableCredit().doubleValue());
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }

  public ChargeAuthorizationResponse toChargeDeclinedResponse(Double availableCredit, String reason) {
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
      default:
        // No acción específica para otros casos
        break;
    }
    return response;
  }

  public PaymentProcessResponse toPaymentSuccessResponse(CreditCard card, BigDecimal requestedAmount,
                                                         BigDecimal actualAmount) {
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

  public PaymentProcessResponse toPaymentFailedResponse(String cardId, BigDecimal requestedAmount,
                                                        PaymentProcessResponse.ErrorCodeEnum errorCode,
                                                        String errorMessage) {
    PaymentProcessResponse response = new PaymentProcessResponse();
    response.setSuccess(false);
    response.setCardId(cardId);
    response.setRequestedAmount(requestedAmount.doubleValue());
    response.setErrorCode(errorCode);
    response.setErrorMessage(errorMessage);
    response.setProcessedAt(OffsetDateTime.now());
    return response;
  }

  public CustomerCardValidationResponse toCustomerValidationResponse(String customerId,
                                                                     List<CreditCard> activeCards) {
    CustomerCardValidationResponse response = new CustomerCardValidationResponse();
    response.setCustomerId(customerId);
    response.setHasActiveCard(!activeCards.isEmpty());
    response.setActiveCardCount(activeCards.size());
    response.setValidatedAt(OffsetDateTime.now());

    if (!activeCards.isEmpty()) {
      List<CustomerCardValidationResponseCardSummaryInner> cardSummary = activeCards.stream()
        .map(this::toCardSummary)
        .collect(Collectors.toList());
      response.setCardSummary(cardSummary);
    }
    return response;
  }

  public TransactionRequest toTransactionRequest(CreditCard card, ChargeAuthorizationRequest request, String authCode) {
    if (card == null || request == null) {
      return null;
    }

    TransactionRequest transactionRequest = new TransactionRequest();
    transactionRequest.setCardId(card.getId());
    transactionRequest.setAmount(request.getAmount());
    transactionRequest.setTransactionType("CHARGE");
    transactionRequest.setAuthorizationCode(authCode);
    transactionRequest.setStatus("APPROVED");
    transactionRequest.setTimestamp(java.time.LocalDateTime.now());
    return transactionRequest;
  }

  public ProductEligibilityResponse toProductEligibilityResponse(String customerId,
                                                                 List<OverdueProduct> overdueProducts) {
    ProductEligibilityResponse response = new ProductEligibilityResponse();
    response.setCustomerId(customerId);
    response.setIsEligible(overdueProducts.isEmpty());
    response.setValidatedAt(OffsetDateTime.now());

    if (overdueProducts.isEmpty()) {
      response.setEligibilityReasons(List.of("NO_OVERDUE_DEBTS"));
      response.setIneligibilityReasons(List.of()); //vacio de momento
      response.setOverdueProducts(List.of());
    } else {
      response.setEligibilityReasons(List.of());
      response.setIneligibilityReasons(List.of("OVERDUE_CREDIT_DEBT"));
      response.setOverdueProducts(overdueProducts);
    }

    return response;
  }

  public CustomerDailyAverageResponse toDailyAverageResponse(String customerId, Integer year, Integer month,
                                                             List<CustomerDailyAverageResponseProductsInner> products) {
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
  public CreditCardSummary toCreditCardSummary(CreditCardResponse cardResponse) {
    if (cardResponse == null) {
      return null;
    }

    CreditCardSummary summary = new CreditCardSummary();
    summary.setCardId(cardResponse.getId());
    summary.setCardNumber(cardResponse.getCardNumber());
    summary.setAvailableCredit(cardResponse.getAvailableCredit() != null ?
      BigDecimal.valueOf(cardResponse.getAvailableCredit()) : BigDecimal.ZERO);
    summary.setCurrentBalance(cardResponse.getCurrentBalance() != null ?
      BigDecimal.valueOf(cardResponse.getCurrentBalance()) : BigDecimal.ZERO);
    summary.setIsActive(cardResponse.getIsActive());
    return summary;
  }

  public List<CreditCardSummary> toCreditCardSummaryList(List<CreditCardResponse> creditCards) {
    if (creditCards == null) {
      return Collections.emptyList();
    }

    return creditCards.stream()
      .map(this::toCreditCardSummary)
      .collect(Collectors.toList());
  }
  public CreditCardCreateRequest secureCreateRequest(
    CreditCardCreateRequest originalRequest,
    String authenticatedCustomerId,
    boolean isAdmin) {

    if (originalRequest == null) {
      return null;
    }

    if (isAdmin) {
      return originalRequest;
    } else {
      CreditCardCreateRequest securedRequest = new CreditCardCreateRequest();
      securedRequest.setCustomerId(authenticatedCustomerId);
      securedRequest.setCreditLimit(
        Optional.ofNullable(originalRequest.getCreditLimit()).orElse(0.0));

      securedRequest.setAvailableCredit(
        Optional.ofNullable(originalRequest.getAvailableCredit()).orElse(0.0));

      securedRequest.setCurrentBalance(
        Optional.ofNullable(originalRequest.getCurrentBalance()).orElse(0.0));
      log.debug("Customer request - original customerId: {}, secured customerId: {}",
        originalRequest.getCustomerId(), authenticatedCustomerId);
      return securedRequest;
    }
  }
}

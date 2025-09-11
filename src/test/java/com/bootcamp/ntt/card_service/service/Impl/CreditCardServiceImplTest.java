package com.bootcamp.ntt.card_service.service.Impl;

import com.bootcamp.ntt.card_service.client.CustomerServiceClient;
import com.bootcamp.ntt.card_service.client.TransactionServiceClient;
import com.bootcamp.ntt.card_service.client.dto.customer.CustomerTypeResponse;
import com.bootcamp.ntt.card_service.client.dto.transaction.TransactionRequest;
import com.bootcamp.ntt.card_service.entity.CreditCard;
import com.bootcamp.ntt.card_service.entity.DailyBalance;
import com.bootcamp.ntt.card_service.enums.CardType;
import com.bootcamp.ntt.card_service.exception.BusinessRuleException;
import com.bootcamp.ntt.card_service.mapper.CreditCardMapper;
import com.bootcamp.ntt.card_service.model.*;
import com.bootcamp.ntt.card_service.repository.CreditCardRepository;
import com.bootcamp.ntt.card_service.repository.DailyBalanceRepository;
import com.bootcamp.ntt.card_service.utils.CardUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceImplTest {

  @Mock
  private CreditCardRepository creditCardRepository;

  @Mock
  private DailyBalanceRepository dailyBalanceRepository;

  @Mock
  private CreditCardMapper creditCardMapper;

  @Mock
  private CustomerServiceClient customerServiceClient;

  @Mock
  private TransactionServiceClient transactionServiceClient;

  @Mock
  private CardUtils cardUtils;

  @InjectMocks
  private CreditCardServiceImpl creditCardService;

  private CreditCard mockCreditCard;
  private CreditCardResponse mockCreditCardResponse;
  private CustomerTypeResponse mockCustomerTypeResponse;
  private CreditCardCreateRequest mockCreateRequest;

  @BeforeEach
  void setUp() {
    mockCreditCard = new CreditCard();
    mockCreditCard.setId("card123");
    mockCreditCard.setCardNumber("4111111111111111");
    mockCreditCard.setCustomerId("customer123");
    mockCreditCard.setActive(true);
    mockCreditCard.setCreditLimit(BigDecimal.valueOf(5000));
    mockCreditCard.setAvailableCredit(BigDecimal.valueOf(3000));
    mockCreditCard.setCurrentBalance(BigDecimal.valueOf(2000));
    mockCreditCard.setType(CardType.CREDIT);

    mockCreditCardResponse = new CreditCardResponse();
    mockCreditCardResponse.setId("card123");
    mockCreditCardResponse.setCardNumber("4111111111111111");
    mockCreditCardResponse.setCustomerId("customer123");

    mockCustomerTypeResponse = new CustomerTypeResponse();
    //mockCustomerTypeResponse.setCustomerId("customer123");
    mockCustomerTypeResponse.setCustomerType("PERSONAL");

    mockCreateRequest = new CreditCardCreateRequest();
    mockCreateRequest.setCustomerId("customer123");
    mockCreateRequest.setCreditLimit(5000.0);
  }

  @Test
  void testGetCardById_Success() {
    // Given
    String cardId = "card123";
    when(creditCardRepository.findById(cardId)).thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toResponse(mockCreditCard)).thenReturn(mockCreditCardResponse);

    // When
    Mono<CreditCardResponse> result = creditCardService.getCardById(cardId);

    // Then
    StepVerifier.create(result)
      .expectNext(mockCreditCardResponse)
      .verifyComplete();

    verify(creditCardRepository).findById(cardId);
    verify(creditCardMapper).toResponse(mockCreditCard);
  }

  @Test
  void testGetCardById_NotFound() {
    // Given
    String cardId = "nonexistent";
    when(creditCardRepository.findById(cardId)).thenReturn(Mono.empty());

    // When
    Mono<CreditCardResponse> result = creditCardService.getCardById(cardId);

    // Then
    StepVerifier.create(result)
      .verifyComplete(); // Se completa sin emitir nada

    verify(creditCardRepository).findById(cardId);
    verify(creditCardMapper, never()).toResponse(any());
  }

  @Test
  void testCreateCard_Success() {
    // Given
    String generatedCardNumber = "4111111111111112";

    when(customerServiceClient.getCustomerType("customer123"))
      .thenReturn(Mono.just(mockCustomerTypeResponse));
    when(creditCardRepository.countByCustomerIdAndIsActiveTrue("customer123"))
      .thenReturn(Mono.just(0L));
    when(cardUtils.generateRandomCardNumber()).thenReturn(generatedCardNumber);
    when(creditCardRepository.findByCardNumber(generatedCardNumber))
      .thenReturn(Mono.empty());
    when(creditCardMapper.toEntity(mockCreateRequest, "PERSONAL", generatedCardNumber))
      .thenReturn(mockCreditCard);
    when(creditCardRepository.save(mockCreditCard)).thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toResponse(mockCreditCard)).thenReturn(mockCreditCardResponse);

    // When
    Mono<CreditCardResponse> result = creditCardService.createCard(mockCreateRequest);

    // Then
    StepVerifier.create(result)
      .expectNext(mockCreditCardResponse)
      .verifyComplete();

    verify(customerServiceClient).getCustomerType("customer123");
    verify(creditCardRepository).countByCustomerIdAndIsActiveTrue("customer123");
    verify(creditCardRepository).save(mockCreditCard);
  }

  @Test
  void testCreateCard_PersonalCustomerAlreadyHasCard() {
    // Given
    when(customerServiceClient.getCustomerType("customer123"))
      .thenReturn(Mono.just(mockCustomerTypeResponse));
    when(creditCardRepository.countByCustomerIdAndIsActiveTrue("customer123"))
      .thenReturn(Mono.just(1L)); // Ya tiene una tarjeta activa

    // When
    Mono<CreditCardResponse> result = creditCardService.createCard(mockCreateRequest);

    // Then
    StepVerifier.create(result)
      .expectError(BusinessRuleException.class)
      .verify();

    verify(customerServiceClient).getCustomerType("customer123");
    verify(creditCardRepository).countByCustomerIdAndIsActiveTrue("customer123");
    verify(creditCardRepository, never()).save(any());
  }

  @Test
  void testAuthorizeCharge_Success() {
    // Given
    String cardNumber = "4111111111111111";
    ChargeAuthorizationRequest request = new ChargeAuthorizationRequest();
    request.setAmount(1000.0);
    //request.setMerchant("Test Store");

    String authCode = "AUTH123";
    ChargeAuthorizationResponse successResponse = new ChargeAuthorizationResponse();
    //successResponse.setApproved(true);
    successResponse.setAuthorizationCode(authCode);

    when(creditCardRepository.findByCardNumber(cardNumber))
      .thenReturn(Mono.just(mockCreditCard));
    when(cardUtils.generateAuthCode()).thenReturn(authCode);
    when(creditCardRepository.save(any(CreditCard.class)))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toTransactionRequest(any(CreditCard.class), eq(request), eq(authCode)))
      .thenReturn(new TransactionRequest());
    when(transactionServiceClient.createTransaction(any(TransactionRequest.class)))
      .thenReturn(Mono.empty());
    when(creditCardMapper.toChargeApprovedResponse(any(CreditCard.class), eq(1000.0), eq(authCode)))
      .thenReturn(successResponse);

    // When
    Mono<ChargeAuthorizationResponse> result = creditCardService.authorizeCharge(cardNumber, request);

    // Then
    StepVerifier.create(result)
      .expectNext(successResponse)
      .verifyComplete();

    verify(creditCardRepository).findByCardNumber(cardNumber);
    verify(creditCardRepository).save(any(CreditCard.class));
    verify(transactionServiceClient).createTransaction(any(TransactionRequest.class));
  }

  @Test
  void testAuthorizeCharge_InsufficientCredit() {
    // Given
    String cardNumber = "4111111111111111";
    ChargeAuthorizationRequest request = new ChargeAuthorizationRequest();
    request.setAmount(5000.0); // Más del crédito disponible (3000)

    ChargeAuthorizationResponse declinedResponse = new ChargeAuthorizationResponse();
    //declinedResponse.setApproved(false);
    declinedResponse.setDeclineReason(ChargeAuthorizationResponse.DeclineReasonEnum.INSUFFICIENT_CREDIT);

    when(creditCardRepository.findByCardNumber(cardNumber))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toChargeDeclinedResponse(3000.0, "INSUFFICIENT_CREDIT"))
      .thenReturn(declinedResponse);

    // When
    Mono<ChargeAuthorizationResponse> result = creditCardService.authorizeCharge(cardNumber, request);

    // Then
    StepVerifier.create(result)
      .expectNext(declinedResponse)
      .verifyComplete();

    verify(creditCardRepository).findByCardNumber(cardNumber);
    verify(creditCardRepository, never()).save(any());
    verify(transactionServiceClient, never()).createTransaction(any());
  }

  @Test
  void testAuthorizeCharge_CardInactive() {
    // Given
    mockCreditCard.setActive(false);
    String cardNumber = "4111111111111111";
    ChargeAuthorizationRequest request = new ChargeAuthorizationRequest();
    request.setAmount(1000.0);

    ChargeAuthorizationResponse declinedResponse = new ChargeAuthorizationResponse();
    //declinedResponse.setApproved(false);
    declinedResponse.setDeclineReason(ChargeAuthorizationResponse.DeclineReasonEnum.CARD_INACTIVE);

    when(creditCardRepository.findByCardNumber(cardNumber))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toChargeDeclinedResponse(3000.0, "CARD_INACTIVE"))
      .thenReturn(declinedResponse);

    // When
    Mono<ChargeAuthorizationResponse> result = creditCardService.authorizeCharge(cardNumber, request);

    // Then
    StepVerifier.create(result)
      .expectNext(declinedResponse)
      .verifyComplete();

    verify(creditCardRepository, never()).save(any());
  }

  @Test
  void testProcessPayment_Success() {
    // Given
    String cardNumber = "4111111111111111";
    PaymentProcessRequest paymentRequest = new PaymentProcessRequest();
    paymentRequest.setAmount(500.0);

    PaymentProcessResponse successResponse = new PaymentProcessResponse();
    successResponse.setSuccess(true);
    successResponse.setActualPaymentAmount(500.0);

    when(creditCardRepository.findByCardNumber(cardNumber))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardRepository.save(any(CreditCard.class)))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toPaymentSuccessResponse(any(CreditCard.class),
      eq(BigDecimal.valueOf(500.0)), eq(BigDecimal.valueOf(500.0))))
      .thenReturn(successResponse);

    // When
    Mono<PaymentProcessResponse> result = creditCardService.processPayment(cardNumber, paymentRequest);

    // Then
    StepVerifier.create(result)
      .expectNext(successResponse)
      .verifyComplete();

    verify(creditCardRepository).findByCardNumber(cardNumber);
    verify(creditCardRepository).save(any(CreditCard.class));
  }

  @Test
  void testProcessPayment_ZeroBalance() {
    // Given
    mockCreditCard.setCurrentBalance(BigDecimal.ZERO);
    String cardNumber = "4111111111111111";
    PaymentProcessRequest paymentRequest = new PaymentProcessRequest();
    paymentRequest.setAmount(500.0);

    PaymentProcessResponse failedResponse = new PaymentProcessResponse();
    failedResponse.setSuccess(false);
    failedResponse.setErrorCode(PaymentProcessResponse.ErrorCodeEnum.ZERO_CURRENT_BALANCE);

    when(creditCardRepository.findByCardNumber(cardNumber))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toPaymentFailedResponse(eq("card123"), eq(BigDecimal.valueOf(500.0)),
      eq(PaymentProcessResponse.ErrorCodeEnum.ZERO_CURRENT_BALANCE), eq("Card has no outstanding balance")))
      .thenReturn(failedResponse);

    // When
    Mono<PaymentProcessResponse> result = creditCardService.processPayment(cardNumber, paymentRequest);

    // Then
    StepVerifier.create(result)
      .expectNext(failedResponse)
      .verifyComplete();

    verify(creditCardRepository, never()).save(any());
  }

  @Test
  void testGetCardsByActive() {
    // Given
    when(creditCardRepository.findByIsActiveAndType(true, CardType.CREDIT))
      .thenReturn(Flux.just(mockCreditCard));
    when(creditCardMapper.toResponse(mockCreditCard))
      .thenReturn(mockCreditCardResponse);

    // When
    Flux<CreditCardResponse> result = creditCardService.getCardsByActive(true);

    // Then
    StepVerifier.create(result)
      .expectNext(mockCreditCardResponse)
      .verifyComplete();

    verify(creditCardRepository).findByIsActiveAndType(true, CardType.CREDIT);
  }

  @Test
  void testDeactivateCard_Success() {
    // Given
    String cardId = "card123";
    when(creditCardRepository.findById(cardId))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardRepository.save(any(CreditCard.class)))
      .thenReturn(Mono.just(mockCreditCard));
    when(creditCardMapper.toResponse(mockCreditCard))
      .thenReturn(mockCreditCardResponse);

    // When
    Mono<CreditCardResponse> result = creditCardService.deactivateCard(cardId);

    // Then
    StepVerifier.create(result)
      .expectNext(mockCreditCardResponse)
      .verifyComplete();

    verify(creditCardRepository).findById(cardId);
    verify(creditCardRepository).save(argThat(card -> !card.isActive()));
  }

  @Test
  void testDeactivateCard_NotFound() {
    // Given
    String cardId = "nonexistent";
    when(creditCardRepository.findById(cardId))
      .thenReturn(Mono.empty());

    // When
    Mono<CreditCardResponse> result = creditCardService.deactivateCard(cardId);

    // Then
    StepVerifier.create(result)
      .expectError(RuntimeException.class)
      .verify();

    verify(creditCardRepository, never()).save(any());
  }

  @Test
  void testGetActiveCardsCount() {
    // Given
    when(creditCardRepository.countByIsActiveAndType(true, CardType.CREDIT))
      .thenReturn(Mono.just(5L));

    // When
    Mono<Integer> result = creditCardService.getActiveCardsCount();

    // Then
    StepVerifier.create(result)
      .expectNext(5)
      .verifyComplete();

    verify(creditCardRepository).countByIsActiveAndType(true, CardType.CREDIT);
  }

  @Test
  void testGenerateUniqueCardNumber_RetryUntilUnique() {
    // Given
    String firstAttempt = "1111111111111111";
    String uniqueNumber = "2222222222222222";

    when(cardUtils.generateRandomCardNumber())
      .thenReturn(firstAttempt)
      .thenReturn(uniqueNumber);
    when(creditCardRepository.findByCardNumber(firstAttempt))
      .thenReturn(Mono.just(mockCreditCard)); // Ya existe
    when(creditCardRepository.findByCardNumber(uniqueNumber))
      .thenReturn(Mono.empty()); // No existe, es único

    // When
    Mono<String> result = creditCardService.generateUniqueCardNumber();

    // Then
    StepVerifier.create(result)
      .expectNext(uniqueNumber)
      .verifyComplete();

    verify(cardUtils, times(2)).generateRandomCardNumber();
    verify(creditCardRepository).findByCardNumber(firstAttempt);
    verify(creditCardRepository).findByCardNumber(uniqueNumber);
  }
}

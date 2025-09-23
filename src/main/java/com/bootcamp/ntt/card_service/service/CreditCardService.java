package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.model.CreditCardResponse;
import com.bootcamp.ntt.card_service.model.ChargeAuthorizationRequest;
import com.bootcamp.ntt.card_service.model.ChargeAuthorizationResponse;
import com.bootcamp.ntt.card_service.model.CreditCardCreateRequest;
import com.bootcamp.ntt.card_service.model.CreditCardUpdateRequest;
import com.bootcamp.ntt.card_service.model.CreditCardBalanceResponse;
import com.bootcamp.ntt.card_service.model.CustomerCardValidationResponse;
import com.bootcamp.ntt.card_service.model.CustomerDailyAverageResponse;
import com.bootcamp.ntt.card_service.model.PaymentProcessRequest;
import com.bootcamp.ntt.card_service.model.PaymentProcessResponse;
import com.bootcamp.ntt.card_service.model.ProductEligibilityResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface CreditCardService {

  //Flux<CreditCardResponse> getAllCards(Boolean isActive);

  Flux<CreditCardResponse> getCardsByActive(Boolean isActive);

  Mono<CreditCardResponse> getCardById(String id);

  Mono<CreditCardResponse> getCardByCardNumber(String cardNumber);

  Flux<CreditCardResponse> getCardsByActiveAndCustomer(Boolean isActive, String customerId);

  Mono<CreditCardResponse> createCard(CreditCardCreateRequest cardRequest);

  Mono<CreditCardResponse> updateCard(String id, CreditCardUpdateRequest cardRequest);

  Mono<Void> deleteCard(String id);

  Mono<CreditCardResponse> deactivateCard(String id);

  Mono<CreditCardResponse> activateCard(String id);

  Mono<ChargeAuthorizationResponse> authorizeCharge(String cardNumber, ChargeAuthorizationRequest request);

  Mono<PaymentProcessResponse> processPayment(String cardNumber, PaymentProcessRequest paymentRequest);

  Mono<CreditCardBalanceResponse> getCardBalance(String cardNumber);

  Mono<CustomerCardValidationResponse> getCustomerCardValidation(String customerId);

  Mono<Void> captureAllDailyBalances();

  Mono<CustomerDailyAverageResponse> getCustomerDailyAverages(String customerId, Integer year, Integer month);

  Mono<ProductEligibilityResponse> checkCustomerProductEligibility(String customerId);

  Mono<Integer> getActiveCardsCount();

  //Flux<CreditCardResponse> getActiveCards(Boolean isActive);
}

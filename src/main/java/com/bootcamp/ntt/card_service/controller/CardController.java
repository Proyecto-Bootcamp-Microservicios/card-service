package com.bootcamp.ntt.card_service.controller;
import com.bootcamp.ntt.card_service.service.CardService;
import com.bootcamp.ntt.cardservice.api.CreditCardsApi;
import com.bootcamp.ntt.cardservice.model.CreditCard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class CardController implements CreditCardsApi {

  @Autowired
  private CardService cardService;

  @Override
  public ResponseEntity<List<CreditCard>> getAllCreditCards() {
    return ResponseEntity.ok(cardService.findAll());
  }
}

package com.bootcamp.ntt.card_service.service;

import com.bootcamp.ntt.card_service.repository.CardRepository;
import com.bootcamp.ntt.cardservice.model.CreditCard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CardService {

  @Autowired
  CardRepository _cardRepository;


  public List<CreditCard> findAll() {
    List<CreditCard> cards = _cardRepository.findAll();
    return cards;
  }
}

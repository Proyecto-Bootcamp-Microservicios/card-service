package com.bootcamp.ntt.card_service.utils;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;

@Component
public class CardUtils {

  private static final SecureRandom random = new SecureRandom();


  public String generateAuthCode() {
    return "AUTH-" + System.currentTimeMillis();
  }

  public String generateRandomCardNumber() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      sb.append(random.nextInt(10));
    }
    return sb.toString();
  }

}

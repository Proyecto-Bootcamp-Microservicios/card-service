package com.bootcamp.ntt.card_service.utils;

import java.time.Duration;

public class CacheKeys {
  public static final Duration MASTER_DATA_TTL = Duration.ofHours(1);
  public static final Duration BALANCE_TTL = Duration.ofMinutes(15);
  public static final Duration ELIGIBILITY_TTL = Duration.ofMinutes(30);
}

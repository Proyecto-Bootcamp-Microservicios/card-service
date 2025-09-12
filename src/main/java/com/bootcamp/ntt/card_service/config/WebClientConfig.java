package com.bootcamp.ntt.card_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  private static final int MEMORY_SIZE_KB = 1024;
  private static final int MAX_MEMORY_SIZE = MEMORY_SIZE_KB * MEMORY_SIZE_KB; // 1MB

  @Bean
  public WebClient webClient() {
    return WebClient.builder()
      .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
      .build();
  }
}

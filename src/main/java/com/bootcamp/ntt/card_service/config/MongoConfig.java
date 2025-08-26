package com.bootcamp.ntt.card_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

import java.util.Optional;

@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {
  @Bean
  public AuditorAware<String> auditorProvider(){
    return () -> Optional.of("system");
  }
}

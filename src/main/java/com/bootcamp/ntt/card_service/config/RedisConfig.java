package com.bootcamp.ntt.card_service.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.text.SimpleDateFormat;

@Configuration
@EnableCaching
public class RedisConfig {

  @Bean
  public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
    ReactiveRedisConnectionFactory factory) {

    Jackson2JsonRedisSerializer<Object> jsonSerializer =
      new Jackson2JsonRedisSerializer<>(Object.class);

    ObjectMapper objectMapper = new ObjectMapper();

    objectMapper.registerModule(new JavaTimeModule());

    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));

    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    objectMapper.activateDefaultTyping(
      objectMapper.getPolymorphicTypeValidator(),
      ObjectMapper.DefaultTyping.NON_FINAL
    );

    jsonSerializer.setObjectMapper(objectMapper);

    RedisSerializationContext<String, Object> context =
      RedisSerializationContext.<String, Object>newSerializationContext(
          new StringRedisSerializer())
        .value(jsonSerializer)
        .build();

    return new ReactiveRedisTemplate<>(factory, context);
  }
}

package com.hexboundrealms.infrastructure.configuration;

import com.hexboundrealms.domain.game.*;
import org.springframework.context.annotation.*;

@Configuration
public class DomainConfiguration {
  @Bean
  GameEngine gameEngine() {
    return new DefaultGameEngine();
  }
}

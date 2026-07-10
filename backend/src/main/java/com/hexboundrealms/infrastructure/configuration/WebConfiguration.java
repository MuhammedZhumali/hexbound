package com.hexboundrealms.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry r) {
    r.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("*").allowedHeaders("*");
  }
}

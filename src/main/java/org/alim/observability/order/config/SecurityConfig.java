package org.alim.observability.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal stand-in security policy for this demo project. It opens the API
 * and the actuator/metrics endpoints so Prometheus can scrape them without
 * credentials, and disables CSRF since the API is a stateless JSON service.
 * A real deployment should replace this with proper authentication.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/actuator/**", "/api/**").permitAll()
        .anyRequest().authenticated()
      );

    return http.build();
  }
}

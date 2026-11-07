package org.alim.common;

import java.util.Locale;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import org.alim.common.security.AppSecurityProperties;
import org.alim.common.security.ProblemDetailSecurityHandlers;
import org.alim.common.web.GlobalExceptionHandler;
import org.alim.common.web.RequestLoggingFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Platform defaults for every servlet service. Runs before Boot's own security
 * auto-configuration so the stateless JWT chain below replaces its form-login default.
 * A service can still define its own {@link SecurityFilterChain} to opt out.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AppSecurityProperties.class)
public class CommonAutoConfiguration {

    /**
     * After Spring's ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1) so traceId/spanId
     * are already in the MDC, and before Spring Security so rejected requests are logged too.
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {

        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public ProblemDetailSecurityHandlers problemDetailSecurityHandlers(ObjectMapper objectMapper) {
        return new ProblemDetailSecurityHandlers(objectMapper);
    }

    /**
     * Reads authorities from the token's {@code roles} claim, e.g. {@code ["USER"]} -> {@code ROLE_USER}.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppSecurityProperties properties,
            ProblemDetailSecurityHandlers problemHandlers,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http
                // Stateless bearer-token API: no cookies or sessions, so nothing for CSRF to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    // Prometheus scrapes without credentials. Move management to its own port when deploying.
                    authorize.requestMatchers("/actuator/**").permitAll();
                    properties.publicPaths().forEach(entry -> permit(authorize, entry));
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                );

        return http.build();
    }

    /**
     * Entry is {@code "<pattern>"} (any method) or {@code "<METHOD> <pattern>"}.
     */
    private static void permit(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorize,
            String entry
    ) {

        String[] parts = entry.trim().split("\\s+", 2);

        if (parts.length == 2) {
            authorize.requestMatchers(HttpMethod.valueOf(parts[0].toUpperCase(Locale.ROOT)), parts[1]).permitAll();
        } else {
            authorize.requestMatchers(parts[0]).permitAll();
        }
    }
}

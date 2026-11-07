package org.alim.user.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@Slf4j
public class JwtConfig {

    /**
     * The signing key as a JWK; its public half is what {@code /.well-known/jwks.json} publishes.
     */
    @Bean
    public RSAKey signingKey(JwtProperties properties) throws JOSEException {

        KeyPair keyPair = loadOrGenerate(properties);

        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                // Thumbprint-based kid is stable for a given key across instances and restarts.
                .keyID(new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).build().computeThumbprint().toString())
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /**
     * Used by this service's own security chain (e.g. {@code GET /users/me}). Defining it also
     * stops Boot from trying to build a decoder from {@code jwk-set-uri}.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties) throws JOSEException {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();

        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud",
                aud -> aud != null && aud.contains(properties.audience())
        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audience
        ));

        return decoder;
    }

    private static KeyPair loadOrGenerate(JwtProperties properties) {

        boolean hasPrivate = StringUtils.hasText(properties.privateKey());
        boolean hasPublic = StringUtils.hasText(properties.publicKey());

        if (hasPrivate != hasPublic) {
            throw new IllegalStateException("app.jwt.private-key and app.jwt.public-key must be set together");
        }

        if (hasPrivate) {
            return new KeyPair(PemKeys.publicKey(properties.publicKey()), PemKeys.privateKey(properties.privateKey()));
        }

        log.warn("No app.jwt keys configured: generating an ephemeral RSA key pair. "
                + "Tokens will be invalid after restart and across instances; do not use outside local development.");

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

package org.alim.user.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param privateKey     PKCS#8 PEM. Leave both keys empty only for local development: an ephemeral
 *                       key pair is generated at startup and every restart invalidates issued tokens.
 * @param publicKey      X.509 (SubjectPublicKeyInfo) PEM matching {@code privateKey}.
 * @param issuer         {@code iss} claim; resource servers must be configured with the same value.
 * @param audience       {@code aud} claim; resource servers reject tokens that don't carry it.
 * @param accessTokenTtl access token lifetime.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        String issuer,
        String audience,
        Duration accessTokenTtl
) {
}

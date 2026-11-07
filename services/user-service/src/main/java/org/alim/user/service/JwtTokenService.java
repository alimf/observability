package org.alim.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import org.alim.user.api.TokenResponse;
import org.alim.user.config.JwtProperties;
import org.alim.user.domain.User;

import lombok.RequiredArgsConstructor;

/**
 * Issues RS256 access tokens. Other services verify them against this service's JWKS endpoint.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenResponse issue(User user) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(user.getRole().name()))
                .claim("email", user.getEmail())
                .build();

        // kid is taken from the signing JWK, so it is set by the encoder's key selection.
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();

        return TokenResponse.bearer(token, properties.accessTokenTtl().toSeconds());
    }
}

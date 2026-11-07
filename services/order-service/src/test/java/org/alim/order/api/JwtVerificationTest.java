package org.alim.order.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.alim.order.service.PaymentClient;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real resource-server decoder (JWKS fetch, signature, issuer, audience, expiry)
 * against a stand-in for user-service's {@code /.well-known/jwks.json}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtVerificationTest {

    private static final String ISSUER = "http://localhost:8081";

    private static final String AUDIENCE = "observability-api";

    private static final RSAKey SIGNING_KEY = generate("test-key");

    private static final HttpServer JWKS_SERVER = startJwksServer();

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/jwks"
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentClient paymentClient;

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void acceptsTokenSignedByUserService() throws Exception {

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(sign(SIGNING_KEY, ISSUER, AUDIENCE, 3600))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsTokenSignedWithUnknownKey() throws Exception {

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(sign(generate("test-key"), ISSUER, AUDIENCE, 3600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongIssuerWrongAudienceAndExpiredTokens() throws Exception {

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(sign(SIGNING_KEY, "http://evil", AUDIENCE, 3600))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(sign(SIGNING_KEY, ISSUER, "other-api", 3600))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(sign(SIGNING_KEY, ISSUER, AUDIENCE, -3600))))
                .andExpect(status().isUnauthorized());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sign(RSAKey key, String issuer, String audience, long ttlSeconds) throws JOSEException {

        Instant now = Instant.now();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audience)
                        .subject(UUID.randomUUID().toString())
                        .issueTime(Date.from(now.minusSeconds(7200)))
                        .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                        .claim("roles", List.of("USER"))
                        .build()
        );
        jwt.sign(new RSASSASigner(key));

        return jwt.serialize();
    }

    private static RSAKey generate(String keyId) {

        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startJwksServer() {

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();

            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

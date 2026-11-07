package org.alim.user.api;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Publishes the public signing key so other services can verify tokens without calling back.
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey signingKey) {
        this.jwks = new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(jwks);
    }
}

package org.alim.user.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth 2.0 token response shape (RFC 6749 section 5.1).
 */
public record TokenResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn
) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}

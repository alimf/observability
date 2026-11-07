package org.alim.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * The user service issues tokens whose {@code sub} is the user's UUID; every other
 * service derives the acting user from that claim and never from request bodies.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidBearerTokenException("Token subject is not a valid user id");
        }
    }
}

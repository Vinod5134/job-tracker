package com.vinod.jobtracker.config;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads our user id out of the validated JWT.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

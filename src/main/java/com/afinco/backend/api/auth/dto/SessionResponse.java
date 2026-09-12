package com.afinco.backend.api.auth.dto;

/** Session probe result; {@code user} is null when the caller is not signed in. */
public record SessionResponse(boolean authenticated, UserResponse user) {

    public static SessionResponse anonymous() {
        return new SessionResponse(false, null);
    }
}

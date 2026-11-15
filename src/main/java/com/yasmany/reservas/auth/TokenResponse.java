package com.yasmany.reservas.auth;

/** expiresIn en segundos, como en OAuth2. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}

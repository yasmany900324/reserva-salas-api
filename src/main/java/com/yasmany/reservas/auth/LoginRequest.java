package com.yasmany.reservas.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "es obligatorio") String usuario,
        @NotBlank(message = "es obligatoria") String password) {
}

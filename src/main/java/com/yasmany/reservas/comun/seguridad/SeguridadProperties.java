package com.yasmany.reservas.comun.seguridad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad (prefijo app.seguridad). Se valida al arrancar:
 * si falta algún valor o el secreto es demasiado corto, la app no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "app.seguridad")
public record SeguridadProperties(
        @NotBlank String usuario,
        @NotBlank String password,
        @Valid @NotNull Jwt jwt) {

    public record Jwt(
            // HS256 exige una clave de al menos 256 bits
            @NotBlank @Size(min = 32, message = "debe tener al menos 32 caracteres") String secreto,
            @NotNull Duration expiracion) {
    }
}

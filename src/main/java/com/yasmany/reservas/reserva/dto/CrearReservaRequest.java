package com.yasmany.reservas.reserva.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CrearReservaRequest(
        @NotNull(message = "es obligatorio")
        Long salaId,

        @NotNull(message = "es obligatorio")
        Long responsableId,

        @Schema(description = "ISO-8601 con zona horaria", example = "2026-12-01T10:00:00Z")
        @NotNull(message = "es obligatorio")
        OffsetDateTime inicio,

        @Schema(description = "ISO-8601 con zona horaria; posterior a inicio", example = "2026-12-01T11:00:00Z")
        @NotNull(message = "es obligatorio")
        OffsetDateTime fin) {
}

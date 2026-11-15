package com.yasmany.reservas.responsable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearResponsableRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String nombre) {
}

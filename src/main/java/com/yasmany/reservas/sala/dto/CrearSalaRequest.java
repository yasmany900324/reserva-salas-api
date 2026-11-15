package com.yasmany.reservas.sala.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearSalaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 20, message = "debe tener como máximo 20 caracteres")
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "debe tener como máximo 100 caracteres")
        String nombre) {
}

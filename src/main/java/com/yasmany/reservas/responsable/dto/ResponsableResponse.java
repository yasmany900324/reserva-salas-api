package com.yasmany.reservas.responsable.dto;

import com.yasmany.reservas.responsable.Responsable;

public record ResponsableResponse(Long id, String nombre) {

    public static ResponsableResponse desde(Responsable responsable) {
        return new ResponsableResponse(responsable.getId(), responsable.getNombre());
    }
}

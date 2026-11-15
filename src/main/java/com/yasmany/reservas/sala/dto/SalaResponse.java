package com.yasmany.reservas.sala.dto;

import com.yasmany.reservas.sala.Sala;

public record SalaResponse(Long id, String codigo, String nombre) {

    public static SalaResponse desde(Sala sala) {
        return new SalaResponse(sala.getId(), sala.getCodigo(), sala.getNombre());
    }
}

package com.yasmany.reservas.reserva.dto;

import com.yasmany.reservas.reserva.EstadoReserva;
import com.yasmany.reservas.reserva.Reserva;
import com.yasmany.reservas.responsable.dto.ResponsableResponse;
import com.yasmany.reservas.sala.dto.SalaResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Las fechas se devuelven siempre en UTC, sea cual sea la zona con la que llegaron. */
public record ReservaResponse(
        Long id,
        SalaResponse sala,
        ResponsableResponse responsable,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        EstadoReserva estado) {

    public static ReservaResponse desde(Reserva reserva) {
        return new ReservaResponse(
                reserva.getId(),
                SalaResponse.desde(reserva.getSala()),
                ResponsableResponse.desde(reserva.getResponsable()),
                reserva.getInicio().withOffsetSameInstant(ZoneOffset.UTC),
                reserva.getFin().withOffsetSameInstant(ZoneOffset.UTC),
                reserva.getEstado());
    }
}

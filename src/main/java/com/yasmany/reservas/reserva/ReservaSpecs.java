package com.yasmany.reservas.reserva;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionales del listado de reservas. Un filtro null no restringe nada. */
final class ReservaSpecs {

    private ReservaSpecs() {
    }

    static Specification<Reserva> deSala(Long salaId) {
        return (root, query, cb) -> salaId == null ? null : cb.equal(root.get("sala").get("id"), salaId);
    }

    static Specification<Reserva> deResponsable(Long responsableId) {
        return (root, query, cb) -> responsableId == null
                ? null
                : cb.equal(root.get("responsable").get("id"), responsableId);
    }

    static Specification<Reserva> conEstado(EstadoReserva estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    /*
     * Juntos, terminaDespuesDe(a) y empiezaAntesDe(b) dan las reservas que ocupan algún momento
     * de [a, b). Por separado permiten rangos abiertos (solo "desde" o solo "hasta").
     */

    static Specification<Reserva> terminaDespuesDe(OffsetDateTime instante) {
        return (root, query, cb) -> instante == null ? null : cb.greaterThan(root.get("fin"), instante);
    }

    static Specification<Reserva> empiezaAntesDe(OffsetDateTime instante) {
        return (root, query, cb) -> instante == null ? null : cb.lessThan(root.get("inicio"), instante);
    }
}

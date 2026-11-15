package com.yasmany.reservas.reserva;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    /**
     * Reservas activas de la sala que se cruzan con [inicio, fin).
     * Dos intervalos semiabiertos se solapan si cada uno empieza antes de que termine el otro.
     */
    @Query("""
            select r from Reserva r
            where r.sala.id = :salaId
              and r.estado = com.yasmany.reservas.reserva.EstadoReserva.ACTIVA
              and r.inicio < :fin
              and r.fin > :inicio
            order by r.inicio
            """)
    List<Reserva> buscarSolapadas(@Param("salaId") Long salaId,
                                  @Param("inicio") OffsetDateTime inicio,
                                  @Param("fin") OffsetDateTime fin);

    /** Carga sala y responsable en la misma consulta para no hacer N+1 al listar. */
    @Override
    @EntityGraph(attributePaths = {"sala", "responsable"})
    Page<Reserva> findAll(Specification<Reserva> spec, Pageable pageable);
}

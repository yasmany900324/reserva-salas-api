package com.yasmany.reservas.reserva;

import com.yasmany.reservas.comun.error.ConflictoException;
import com.yasmany.reservas.comun.error.DatosInvalidosException;
import com.yasmany.reservas.comun.error.RecursoNoEncontradoException;
import com.yasmany.reservas.reserva.dto.CrearReservaRequest;
import com.yasmany.reservas.reserva.dto.ReservaResponse;
import com.yasmany.reservas.responsable.Responsable;
import com.yasmany.reservas.responsable.ResponsableRepository;
import com.yasmany.reservas.sala.Sala;
import com.yasmany.reservas.sala.SalaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReservaService {

    /** Nombre de la restricción de exclusión de la migración V1. */
    static final String RESTRICCION_SOLAPAMIENTO = "ex_reserva_sin_solapamiento";

    private final ReservaRepository reservaRepository;
    private final SalaRepository salaRepository;
    private final ResponsableRepository responsableRepository;
    private final Clock clock;
    private final ZoneId zonaFiltroFecha;

    public ReservaService(ReservaRepository reservaRepository,
                          SalaRepository salaRepository,
                          ResponsableRepository responsableRepository,
                          Clock clock,
                          @Value("${app.reservas.zona-filtro-fecha}") ZoneId zonaFiltroFecha) {
        this.reservaRepository = reservaRepository;
        this.salaRepository = salaRepository;
        this.responsableRepository = responsableRepository;
        this.clock = clock;
        this.zonaFiltroFecha = zonaFiltroFecha;
    }

    @Transactional
    public ReservaResponse crear(CrearReservaRequest request) {
        // Se guardan en UTC: el instante es el mismo, solo cambia cómo se escribe
        OffsetDateTime inicio = request.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime fin = request.fin().withOffsetSameInstant(ZoneOffset.UTC);

        if (!fin.isAfter(inicio)) {
            throw new DatosInvalidosException("fin", "debe ser posterior a inicio");
        }
        if (inicio.isBefore(OffsetDateTime.now(clock))) {
            throw new DatosInvalidosException("inicio", "no puede estar en el pasado");
        }

        Sala sala = salaRepository.findById(request.salaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la sala con id " + request.salaId()));
        Responsable responsable = responsableRepository.findById(request.responsableId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el responsable con id " + request.responsableId()));

        // Comprobación previa: da un 409 que dice con qué reserva choca
        List<Reserva> solapadas = reservaRepository.buscarSolapadas(sala.getId(), inicio, fin);
        if (!solapadas.isEmpty()) {
            Reserva otra = solapadas.get(0);
            throw new ConflictoException("La sala " + sala.getCodigo() + " ya está reservada en ese horario: "
                    + "choca con la reserva " + otra.getId() + " (" + utc(otra.getInicio()) + " a "
                    + utc(otra.getFin()) + ")");
        }

        try {
            // saveAndFlush para que la restricción de exclusión salte aquí y no al hacer commit
            Reserva reserva = reservaRepository.saveAndFlush(new Reserva(sala, responsable, inicio, fin));
            return ReservaResponse.desde(reserva);
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones simultáneas pasaron la comprobación previa: la BD decide cuál entra.
            // La transacción queda marcada para rollback; solo relanzamos el error traducido.
            if (violaRestriccion(e, RESTRICCION_SOLAPAMIENTO)) {
                throw new ConflictoException("La sala " + sala.getCodigo()
                        + " ya está reservada en ese horario por otra petición simultánea");
            }
            throw e;
        }
    }

    /**
     * Filtro por días: "fecha" es un día concreto y "desde"/"hasta" un rango de días, ambos incluidos.
     * Devuelve las reservas que ocupan algún momento del periodo, no solo las que empiezan en él.
     * Los días se interpretan en la zona configurada (UTC por defecto).
     */
    public Page<ReservaResponse> listar(Long salaId, Long responsableId, LocalDate fecha,
                                        LocalDate desde, LocalDate hasta,
                                        EstadoReserva estado, Pageable pageable) {
        if (fecha != null && (desde != null || hasta != null)) {
            throw new DatosInvalidosException("fecha", "no se puede combinar con desde/hasta; usa uno u otro");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new DatosInvalidosException("desde", "debe ser anterior o igual a hasta");
        }
        if (fecha != null) {
            desde = fecha;
            hasta = fecha;
        }
        // [00:00 del primer día, 00:00 del día siguiente al último)
        OffsetDateTime inicioPeriodo = desde == null ? null : desde.atStartOfDay(zonaFiltroFecha).toOffsetDateTime();
        OffsetDateTime finPeriodo = hasta == null
                ? null
                : hasta.plusDays(1).atStartOfDay(zonaFiltroFecha).toOffsetDateTime();
        Specification<Reserva> filtro = Specification.allOf(
                ReservaSpecs.deSala(salaId),
                ReservaSpecs.deResponsable(responsableId),
                ReservaSpecs.terminaDespuesDe(inicioPeriodo),
                ReservaSpecs.empiezaAntesDe(finPeriodo),
                ReservaSpecs.conEstado(estado));
        return reservaRepository.findAll(filtro, pageable).map(ReservaResponse::desde);
    }

    public ReservaResponse obtener(Long id) {
        return ReservaResponse.desde(buscar(id));
    }

    @Transactional
    public ReservaResponse cancelar(Long id) {
        Reserva reserva = buscar(id);
        if (!reserva.estaActiva()) {
            throw new ConflictoException("La reserva " + id + " ya está cancelada");
        }
        if (!reserva.getFin().isAfter(OffsetDateTime.now(clock))) {
            throw new ConflictoException("La reserva " + id + " ya terminó y no se puede cancelar");
        }
        reserva.cancelar();
        return ReservaResponse.desde(reserva);
    }

    private Reserva buscar(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la reserva con id " + id));
    }

    private static OffsetDateTime utc(OffsetDateTime fecha) {
        return fecha.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static boolean violaRestriccion(Throwable e, String restriccion) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(restriccion)) {
                return true;
            }
        }
        return false;
    }
}

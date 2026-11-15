package com.yasmany.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reglas de negocio de las reservas, sin Spring ni base de datos.
 * El reloj está fijo en 2030-01-01 12:00Z para que "pasado" y "ya terminó" sean deterministas.
 */
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2030-01-01T12:00:00Z");
    private static final Long SALA_ID = 1L;
    private static final Long RESPONSABLE_ID = 2L;

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private SalaRepository salaRepository;
    @Mock
    private ResponsableRepository responsableRepository;

    private ReservaService service;
    private Sala sala;
    private Responsable responsable;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(Instant.from(AHORA), ZoneOffset.UTC);
        service = new ReservaService(reservaRepository, salaRepository, responsableRepository, reloj, ZoneOffset.UTC);
        sala = conId(new Sala("A-101", "Sala Andes"), SALA_ID);
        responsable = conId(new Responsable("Ana Pérez"), RESPONSABLE_ID);
    }

    @Nested
    class Crear {

        @Test
        void creaLaReservaYDevuelveLasFechasEnUtc() {
            existenSalaYResponsable();
            when(reservaRepository.buscarSolapadas(any(), any(), any())).thenReturn(List.of());
            when(reservaRepository.saveAndFlush(any())).thenAnswer(inv -> conId(inv.getArgument(0), 10L));

            // Llega en hora de Montevideo (-03:00): 10:00-03:00 son las 13:00Z
            ReservaResponse r = service.crear(peticion("2030-01-02T10:00:00-03:00", "2030-01-02T11:00:00-03:00"));

            assertThat(r.id()).isEqualTo(10L);
            assertThat(r.estado()).isEqualTo(EstadoReserva.ACTIVA);
            assertThat(r.inicio()).isEqualTo(OffsetDateTime.parse("2030-01-02T13:00:00Z"));
            assertThat(r.fin()).isEqualTo(OffsetDateTime.parse("2030-01-02T14:00:00Z"));
        }

        @Test
        void rechazaConConflictoSiSeSolapaConOtraReservaActiva() {
            existenSalaYResponsable();
            Reserva existente = conId(new Reserva(sala, responsable,
                    OffsetDateTime.parse("2030-01-02T10:00:00Z"), OffsetDateTime.parse("2030-01-02T11:00:00Z")), 7L);
            when(reservaRepository.buscarSolapadas(any(), any(), any())).thenReturn(List.of(existente));

            assertThatThrownBy(() -> service.crear(peticion("2030-01-02T10:30:00Z", "2030-01-02T11:30:00Z")))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("choca con la reserva 7");
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void traduceAConflictoLaRestriccionDeLaBaseDeDatosEnPeticionesSimultaneas() {
            existenSalaYResponsable();
            when(reservaRepository.buscarSolapadas(any(), any(), any())).thenReturn(List.of());
            when(reservaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                    "conflicting key value violates exclusion constraint \"ex_reserva_sin_solapamiento\""));

            assertThatThrownBy(() -> service.crear(peticion("2030-01-02T10:00:00Z", "2030-01-02T11:00:00Z")))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("petición simultánea");
        }

        @Test
        void rechazaFinIgualOAnteriorAInicio() {
            assertThatThrownBy(() -> service.crear(peticion("2030-01-02T10:00:00Z", "2030-01-02T10:00:00Z")))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampo()).isEqualTo("fin"));
            assertThatThrownBy(() -> service.crear(peticion("2030-01-02T11:00:00Z", "2030-01-02T10:00:00Z")))
                    .isInstanceOf(DatosInvalidosException.class);
        }

        @Test
        void rechazaReservasEnElPasado() {
            assertThatThrownBy(() -> service.crear(peticion("2030-01-01T11:00:00Z", "2030-01-01T13:00:00Z")))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampo()).isEqualTo("inicio"));
        }

        @Test
        void devuelveNoEncontradoSiLaSalaNoExiste() {
            when(salaRepository.findById(SALA_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.crear(peticion("2030-01-02T10:00:00Z", "2030-01-02T11:00:00Z")))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("sala");
        }
    }

    @Nested
    class Cancelar {

        @Test
        void cancelaUnaReservaActivaFutura() {
            Reserva reserva = reserva("2030-01-02T10:00:00Z", "2030-01-02T11:00:00Z");
            when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

            assertThat(service.cancelar(5L).estado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void rechazaCancelarDosVeces() {
            Reserva reserva = reserva("2030-01-02T10:00:00Z", "2030-01-02T11:00:00Z");
            reserva.cancelar();
            when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

            assertThatThrownBy(() -> service.cancelar(5L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("ya está cancelada");
        }

        @Test
        void rechazaCancelarUnaReservaQueYaTermino() {
            // Terminó a las 11:59:59Z y el reloj marca las 12:00Z
            Reserva reserva = reserva("2030-01-01T11:00:00Z", "2030-01-01T11:59:59Z");
            when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

            assertThatThrownBy(() -> service.cancelar(5L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("ya terminó");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        }
    }

    @Nested
    class Listar {

        @Test
        void rechazaCombinarFechaConRango() {
            LocalDate dia = LocalDate.of(2030, 1, 2);

            assertThatThrownBy(() -> service.listar(null, null, dia, dia, null, null, Pageable.unpaged()))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampo()).isEqualTo("fecha"));
        }

        @Test
        void rechazaRangoConDesdePosteriorAHasta() {
            assertThatThrownBy(() -> service.listar(null, null, null,
                    LocalDate.of(2030, 1, 5), LocalDate.of(2030, 1, 2), null, Pageable.unpaged()))
                    .isInstanceOfSatisfying(DatosInvalidosException.class,
                            e -> assertThat(e.getCampo()).isEqualTo("desde"));
        }
    }

    private void existenSalaYResponsable() {
        when(salaRepository.findById(SALA_ID)).thenReturn(Optional.of(sala));
        when(responsableRepository.findById(RESPONSABLE_ID)).thenReturn(Optional.of(responsable));
    }

    private Reserva reserva(String inicio, String fin) {
        return conId(new Reserva(sala, responsable, OffsetDateTime.parse(inicio), OffsetDateTime.parse(fin)), 5L);
    }

    private static CrearReservaRequest peticion(String inicio, String fin) {
        return new CrearReservaRequest(SALA_ID, RESPONSABLE_ID, OffsetDateTime.parse(inicio), OffsetDateTime.parse(fin));
    }

    /** Las entidades no exponen setId: el id lo asigna la base de datos. */
    private static <T> T conId(T entidad, Long id) {
        ReflectionTestUtils.setField(entidad, "id", id);
        return entidad;
    }
}

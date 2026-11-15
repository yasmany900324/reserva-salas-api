package com.yasmany.reservas.reserva;

import com.yasmany.reservas.responsable.Responsable;
import com.yasmany.reservas.sala.Sala;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // constructor vacío que exige JPA
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // La relación solo vive en este lado: Sala y Responsable no tienen List<Reserva>
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_id", nullable = false)
    private Sala sala;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsable_id", nullable = false)
    private Responsable responsable;

    @Column(nullable = false)
    private OffsetDateTime inicio;

    @Column(nullable = false)
    private OffsetDateTime fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    public Reserva(Sala sala, Responsable responsable, OffsetDateTime inicio, OffsetDateTime fin) {
        this.sala = sala;
        this.responsable = responsable;
        this.inicio = inicio;
        this.fin = fin;
        this.estado = EstadoReserva.ACTIVA;
    }

    /** Cambia el estado a CANCELADA. Las reglas (ya cancelada, ya terminada) las valida el servicio. */
    public void cancelar() {
        this.estado = EstadoReserva.CANCELADA;
    }

    public boolean estaActiva() {
        return estado == EstadoReserva.ACTIVA;
    }
}

package com.yasmany.reservas.reserva;

import com.yasmany.reservas.reserva.dto.CrearReservaRequest;
import com.yasmany.reservas.reserva.dto.ReservaResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Reservas", description = "Crear, consultar y cancelar reservas de salas")
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Reserva creada; la cabecera Location apunta a ella")
    @ApiResponse(responseCode = "404", description = "No existe la sala o el responsable",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "La sala ya está reservada en ese horario",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest request) {
        ReservaResponse reserva = reservaService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reserva.id())
                .toUri();
        return ResponseEntity.created(location).body(reserva);
    }

    @GetMapping
    public Page<ReservaResponse> listar(
            @Parameter(description = "Solo las reservas de esta sala")
            @RequestParam(required = false) Long salaId,
            @Parameter(description = "Solo las reservas que ocupan algún momento de este día (UTC por defecto)",
                    example = "2026-12-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @Parameter(description = "Inicio del rango de días, incluido. No se combina con fecha",
                    example = "2026-12-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fin del rango de días, incluido. No se combina con fecha",
                    example = "2026-12-07")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Solo las reservas de este responsable")
            @RequestParam(required = false) Long responsableId,
            @Parameter(description = "Solo las reservas en este estado; sin él se listan activas y canceladas")
            @RequestParam(required = false) EstadoReserva estado,
            @ParameterObject @SortDefault("inicio") Pageable pageable) {
        return reservaService.listar(salaId, responsableId, fecha, desde, hasta, estado, pageable);
    }

    @GetMapping("/{id}")
    public ReservaResponse obtener(@PathVariable Long id) {
        return reservaService.obtener(id);
    }

    @PatchMapping("/{id}/cancelar")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada; el horario queda libre")
    @ApiResponse(responseCode = "409", description = "La reserva ya estaba cancelada o ya terminó",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ReservaResponse cancelar(@PathVariable Long id) {
        return reservaService.cancelar(id);
    }
}

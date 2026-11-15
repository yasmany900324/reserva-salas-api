package com.yasmany.reservas.responsable;

import com.yasmany.reservas.responsable.dto.CrearResponsableRequest;
import com.yasmany.reservas.responsable.dto.ResponsableResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Ampliación propia sobre el enunciado: por ahora solo crear y consultar (decisión D-1). */
@Tag(name = "Responsables", description = "Crear y consultar las personas responsables de las reservas")
@RestController
@RequestMapping("/responsables")
@RequiredArgsConstructor
public class ResponsableController {

    private final ResponsableService responsableService;

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Responsable creado; la cabecera Location apunta a él")
    public ResponseEntity<ResponsableResponse> crear(@Valid @RequestBody CrearResponsableRequest request) {
        ResponsableResponse responsable = responsableService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responsable.id())
                .toUri();
        return ResponseEntity.created(location).body(responsable);
    }

    @GetMapping
    public Page<ResponsableResponse> listar(@ParameterObject @SortDefault("nombre") Pageable pageable) {
        return responsableService.listar(pageable);
    }

    @GetMapping("/{id}")
    public ResponsableResponse obtener(@PathVariable Long id) {
        return responsableService.obtener(id);
    }
}

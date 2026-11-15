package com.yasmany.reservas.sala;

import com.yasmany.reservas.sala.dto.CrearSalaRequest;
import com.yasmany.reservas.sala.dto.SalaResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/salas")
@RequiredArgsConstructor
public class SalaController {

    private final SalaService salaService;

    @PostMapping
    public ResponseEntity<SalaResponse> crear(@Valid @RequestBody CrearSalaRequest request) {
        SalaResponse sala = salaService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(sala.id())
                .toUri();
        return ResponseEntity.created(location).body(sala);
    }

    @GetMapping
    public Page<SalaResponse> listar(@SortDefault("codigo") Pageable pageable) {
        return salaService.listar(pageable);
    }

    @GetMapping("/{id}")
    public SalaResponse obtener(@PathVariable Long id) {
        return salaService.obtener(id);
    }
}

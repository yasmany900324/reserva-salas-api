package com.yasmany.reservas.sala;

import com.yasmany.reservas.comun.error.ConflictoException;
import com.yasmany.reservas.comun.error.RecursoNoEncontradoException;
import com.yasmany.reservas.sala.dto.CrearSalaRequest;
import com.yasmany.reservas.sala.dto.SalaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalaService {

    private final SalaRepository salaRepository;

    @Transactional
    public SalaResponse crear(CrearSalaRequest request) {
        String codigo = request.codigo().trim();
        // Comprobación previa para dar un mensaje claro en el caso normal
        if (salaRepository.existsByCodigo(codigo)) {
            throw codigoDuplicado(codigo);
        }
        try {
            // saveAndFlush para que la restricción UNIQUE salte aquí y no al hacer commit
            Sala sala = salaRepository.saveAndFlush(new Sala(codigo, request.nombre().trim()));
            return SalaResponse.desde(sala);
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones simultáneas con el mismo código: la BD (uk_sala_codigo) decide
            throw codigoDuplicado(codigo);
        }
    }

    public Page<SalaResponse> listar(Pageable pageable) {
        return salaRepository.findAll(pageable).map(SalaResponse::desde);
    }

    public SalaResponse obtener(Long id) {
        return salaRepository.findById(id)
                .map(SalaResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la sala con id " + id));
    }

    private static ConflictoException codigoDuplicado(String codigo) {
        return new ConflictoException("Ya existe una sala con el código '" + codigo + "'");
    }
}

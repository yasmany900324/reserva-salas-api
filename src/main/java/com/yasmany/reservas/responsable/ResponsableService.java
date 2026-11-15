package com.yasmany.reservas.responsable;

import com.yasmany.reservas.comun.error.RecursoNoEncontradoException;
import com.yasmany.reservas.responsable.dto.CrearResponsableRequest;
import com.yasmany.reservas.responsable.dto.ResponsableResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResponsableService {

    private final ResponsableRepository responsableRepository;

    @Transactional
    public ResponsableResponse crear(CrearResponsableRequest request) {
        Responsable responsable = responsableRepository.save(new Responsable(request.nombre().trim()));
        return ResponsableResponse.desde(responsable);
    }

    public Page<ResponsableResponse> listar(Pageable pageable) {
        return responsableRepository.findAll(pageable).map(ResponsableResponse::desde);
    }

    public ResponsableResponse obtener(Long id) {
        return responsableRepository.findById(id)
                .map(ResponsableResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el responsable con id " + id));
    }
}

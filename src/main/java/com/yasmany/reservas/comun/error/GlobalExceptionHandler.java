package com.yasmany.reservas.comun.error;

import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones a respuestas Problem Details (RFC 9457) con un formato uniforme.
 * Hereda de ResponseEntityExceptionHandler, que ya cubre los errores estándar de Spring MVC
 * (método no permitido, media type no soportado, etc.).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record ErrorCampo(String campo, String mensaje) {
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail manejarConflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    /** Red de seguridad: una restricción de la base de datos rechazó la operación. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarIntegridad(DataIntegrityViolationException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto",
                "La operación entra en conflicto con datos existentes");
    }

    /** Parámetro de ruta o de consulta con tipo incorrecto, p. ej. /salas/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "El valor '" + ex.getValue() + "' no es válido para el parámetro '" + ex.getName() + "'");
    }

    /** Ordenación por un campo que no existe, p. ej. ?sort=foo */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail manejarOrdenInvalido(PropertyReferenceException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "No se puede ordenar por '" + ex.getPropertyName() + "'");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<ErrorCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorCampo(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorCampo::campo).thenComparing(ErrorCampo::mensaje))
                .toList();
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "La petición tiene campos inválidos");
        problema.setProperty("errors", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Cuerpo inválido",
                "El cuerpo de la petición no es un JSON válido o algún campo tiene un formato incorrecto"));
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}

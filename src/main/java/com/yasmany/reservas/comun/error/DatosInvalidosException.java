package com.yasmany.reservas.comun.error;

/**
 * Se traduce a 400 con el mismo formato que los errores de Bean Validation.
 * Para reglas que dependen de varios campos o del reloj (fin > inicio, no en el pasado).
 */
public class DatosInvalidosException extends RuntimeException {

    private final String campo;

    public DatosInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}

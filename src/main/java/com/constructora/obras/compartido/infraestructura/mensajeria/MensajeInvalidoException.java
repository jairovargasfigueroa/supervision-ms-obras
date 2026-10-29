package com.constructora.obras.compartido.infraestructura.mensajeria;

/**
 * El mensaje no se puede procesar nunca (no es JSON, versión distinta de 1, datos incompletos o un tipo sin
 * consumidor): va directo a plataforma.fallidos, sin reintentos.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

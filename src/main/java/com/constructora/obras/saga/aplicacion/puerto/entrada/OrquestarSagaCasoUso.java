package com.constructora.obras.saga.aplicacion.puerto.entrada;

import com.constructora.obras.saga.dominio.RespuestaSaga;
import java.util.UUID;

/**
 * Orquestación de la saga «aprobar planilla» (008): aplica las respuestas de Finanzas y Materiales. Corre en la
 * transacción del consumidor, la misma que registra el mensaje en mensaje_procesado (un repetido no se aplica dos
 * veces).
 */
public interface OrquestarSagaCasoUso {

    /**
     * @param sagaId     correlacionId del mensaje
     * @param planillaId datos.planillaId del mensaje
     * @throws RespuestaSagaInvalidaException si no hay saga con ese id o es de otra planilla (el mensaje no se puede
     *                                        procesar nunca)
     */
    void recibir(UUID sagaId, UUID planillaId, RespuestaSaga respuesta);

    /** La respuesta no corresponde a ninguna saga de Obras: el consumidor la manda a plataforma.fallidos. */
    class RespuestaSagaInvalidaException extends RuntimeException {

        public RespuestaSagaInvalidaException(String mensaje) {
            super(mensaje);
        }
    }
}

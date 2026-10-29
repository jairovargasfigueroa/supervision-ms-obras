package com.constructora.obras.saga.aplicacion.puerto.entrada;

import com.constructora.obras.saga.dominio.RespuestaSaga;
import java.util.List;
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

    /** Ids de las sagas EN_CURSO cuyo paso actual ya venció (lo usa la tarea que corre cada 10 s). */
    List<UUID> sagasVencidas();

    /**
     * Trata como fallo el paso vencido de esa saga y compensa lo hecho (criterio 11), en su propia transacción. Si
     * entre tanto llegó la respuesta (o la saga ya no está EN_CURSO), no cambia nada.
     */
    void vencer(UUID sagaId);

    /** La respuesta no corresponde a ninguna saga de Obras: el consumidor la manda a plataforma.fallidos. */
    class RespuestaSagaInvalidaException extends RuntimeException {

        public RespuestaSagaInvalidaException(String mensaje) {
            super(mensaje);
        }
    }
}

package com.constructora.obras.saga.aplicacion.puerto.salida;

import com.constructora.obras.saga.dominio.SagaAprobacion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Sagas «aprobar planilla» (tabla saga_aprobacion). Corre dentro de la transacción del caso de uso. */
public interface SagaAprobacionRepositorio {

    /** Guarda una saga nueva o los cambios de una existente. */
    void guardar(SagaAprobacion saga);

    /**
     * La saga, bloqueada hasta el final de la transacción: una respuesta y la tarea de vencimiento no la cambian a
     * la vez.
     */
    Optional<SagaAprobacion> buscarParaActualizar(UUID id);

    /** Ids de las sagas EN_CURSO cuyo paso actual venció antes de ese instante. */
    List<UUID> vencidas(Instant ahora);
}

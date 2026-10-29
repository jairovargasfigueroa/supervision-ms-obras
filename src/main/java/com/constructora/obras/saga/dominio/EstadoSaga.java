package com.constructora.obras.saga.dominio;

/** Estados de la saga «aprobar planilla» (docs/datos.md, saga_aprobacion.estado). */
public enum EstadoSaga {
    /** Espera la respuesta del paso actual. */
    EN_CURSO,
    /** Todos los pasos terminaron bien: la planilla quedó APROBADA. */
    COMPLETADA,
    /** Falló un paso (o no respondió a tiempo): se enviaron las compensaciones y la planilla quedó OBSERVADA. */
    COMPENSADA
}

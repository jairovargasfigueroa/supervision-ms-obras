package com.constructora.obras.obra.aplicacion.puerto.entrada;

import java.util.UUID;

/** 002 · Respuesta de Finanzas al registrar el contrato de una obra (eventos ContratoRegistrado y ContratoRechazado). */
public interface RegistrarRespuestaContratoCasoUso {

    /** Guarda el contratoId y pasa la obra a EN_EJECUCION. */
    void contratoRegistrado(UUID obraId, UUID contratoId);

    /** La obra queda en SIN_CONTRATO con el motivo visible en su detalle. */
    void contratoRechazado(UUID obraId, String motivo);
}

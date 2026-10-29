package com.constructora.obras.planilla.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.planilla.dominio.EstadoPlanilla;
import java.util.UUID;

/**
 * El cliente revisa en el portal una planilla PRESENTADA (008, criterios 3 a 6): la aprueba (empieza la saga) o la
 * observa con un motivo. Solo el cliente de la obra: si ve la obra con otro papel, SIN_PERMISO; si no la ve (o la
 * planilla es un BORRADOR y la ve solo como cliente), PLANILLA_NO_ENCONTRADA; si ya no está PRESENTADA,
 * PLANILLA_NO_PRESENTADA.
 */
public interface RevisarPlanillaCasoUso {

    /** Pasa la planilla a APROBANDO, crea la saga y envía ReservarSaldo. Devuelve el estado nuevo (APROBANDO). */
    EstadoPlanilla aprobar(UUID planillaId, Usuario usuario);

    /**
     * Pasa la planilla a OBSERVADA con el motivo (10 a 500 caracteres, MOTIVO_INVALIDO) y publica
     * PlanillaObservada con origen CLIENTE.
     */
    PlanillaDetalle observar(UUID planillaId, String motivo, Usuario usuario);
}

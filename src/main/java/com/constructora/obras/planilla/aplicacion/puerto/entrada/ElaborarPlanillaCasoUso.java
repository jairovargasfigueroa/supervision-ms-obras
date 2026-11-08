package com.constructora.obras.planilla.aplicacion.puerto.entrada;

import com.constructora.obras.compartido.dominio.Periodo;
import com.constructora.obras.compartido.dominio.Usuario;
import com.constructora.obras.planilla.dominio.Planilla.CantidadLinea;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Elaborar y presentar planillas (007). Solo el residente asignado a la obra (criterio 10): si ve la obra con otro
 * papel, SIN_PERMISO; si no la ve, OBRA_NO_ENCONTRADA (crear) o PLANILLA_NO_ENCONTRADA (guardar y presentar).
 */
public interface ElaborarPlanillaCasoUso {

    /** Crea la planilla del periodo en BORRADOR (criterios 1 y 2). */
    PlanillaDetalle crear(UUID obraId, Periodo periodo, Usuario usuario);

    /** Guarda el borrador: cantidades y fotos adjuntas, con la versión que se editó (criterios 5 a 7 y 9). */
    PlanillaDetalle guardar(Guardado guardado, Usuario usuario);

    /** Presenta la planilla y publica sus fotos adjuntas para el cliente (criterio 8). */
    PlanillaDetalle presentar(UUID planillaId, int version, Usuario usuario);

    /**
     * Cuerpo de PUT /planillas/{id}.
     *
     * @param lineas  cantidad del periodo por partida (las que no vienen quedan como estaban)
     * @param fotoIds fotos adjuntas (reemplazan a las anteriores)
     */
    record Guardado(UUID planillaId, int version, List<CantidadLinea> lineas, List<UUID> fotoIds) {

        public Guardado {
            Objects.requireNonNull(planillaId, "planillaId");
            lineas = List.copyOf(lineas);
            fotoIds = List.copyOf(fotoIds);
        }
    }
}

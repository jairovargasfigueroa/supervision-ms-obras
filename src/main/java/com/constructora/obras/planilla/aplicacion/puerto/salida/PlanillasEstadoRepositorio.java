package com.constructora.obras.planilla.aplicacion.puerto.salida;

import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillasEstadoCasoUso.Filtro;
import com.constructora.obras.planilla.aplicacion.puerto.entrada.ConsultarPlanillasEstadoCasoUso.PlanillaEstado;
import java.util.List;

/** 501 · Lectura de planillas de varias obras por estado (con el nombre de la obra), para planillas_estado. */
public interface PlanillasEstadoRepositorio {

    /**
     * Planillas que cumplen el filtro, por periodo descendente, nombre de la obra y número.
     *
     * @param maximo filas máximas (quien llama pide una más que su límite para saber si hay más)
     */
    List<PlanillaEstado> buscar(Filtro filtro, int maximo);
}

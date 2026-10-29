package com.constructora.obras.obra.aplicacion.puerto.entrada;

import com.constructora.obras.obra.dominio.Obra;
import com.constructora.obras.planilla.dominio.PlanillaCorta;

/**
 * Obra con sus indicadores calculados a la fecha (avance, plazo y riesgo, y por partida ejecutada acumulada y avance)
 * y su última planilla (la de periodo más reciente, en cualquier estado; null si no tiene).
 */
public record ObraDetalle(Obra obra, Obra.Indicadores indicadores, PlanillaCorta ultimaPlanilla) {
}

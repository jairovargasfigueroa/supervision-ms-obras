package com.constructora.obras.obra.aplicacion.puerto.entrada;

import com.constructora.obras.obra.dominio.Obra;

/** Obra con sus indicadores calculados a la fecha (avance, plazo y riesgo). */
public record ObraDetalle(Obra obra, Obra.Indicadores indicadores) {
}

package com.constructora.obras.planilla.aplicacion.puerto.entrada;

import com.constructora.obras.planilla.dominio.Planilla;
import com.constructora.obras.planilla.dominio.Planilla.LineaDetalle;
import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que devuelve GET /planillas/{id} (y crear, guardar y presentar): la planilla, sus líneas con los datos de la
 * partida y los acumulados, y los porcentajes del contrato de la obra.
 */
public record PlanillaDetalle(Planilla planilla, List<LineaDetalle> lineas, BigDecimal anticipoPct,
        BigDecimal retencionPct) {
}

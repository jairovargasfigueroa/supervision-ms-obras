package com.constructora.obras.obra.infraestructura.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de GET /obras (docs/contratos.md). ultimaPlanilla es null si la obra no tiene planillas; zona, sin zona (401). */
public record ObraResumenResponse(
        UUID id,
        String nombre,
        String ubicacion,
        String zona,
        String estado,
        LocalDate fechaInicio,
        BigDecimal avancePct,
        BigDecimal plazoPct,
        boolean enRiesgo,
        PlanillaCorta ultimaPlanilla) {

    public record PlanillaCorta(String periodo, String estado) {
    }
}

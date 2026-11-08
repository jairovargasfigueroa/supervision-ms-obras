package com.constructora.obras.planilla.infraestructura.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Elemento de GET /obras/{obraId}/planillas (docs/contratos.md). cobroId y cobroNumero son null hasta la 008. */
public record PlanillaResumenResponse(UUID id, int numero, String periodo, String estado, BigDecimal neto,
        UUID cobroId, String cobroNumero) {
}

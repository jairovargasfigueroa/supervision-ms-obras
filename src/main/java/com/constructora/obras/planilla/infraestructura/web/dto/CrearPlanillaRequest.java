package com.constructora.obras.planilla.infraestructura.web.dto;

/** Cuerpo de POST /obras/{obraId}/planillas (docs/contratos.md): periodo AAAA-MM. */
public record CrearPlanillaRequest(String periodo) {
}

package com.constructora.obras.planilla.infraestructura.web.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de POST /planillas/{id}/presentar (docs/contratos.md): versión que se presenta. */
public record PresentarPlanillaRequest(@NotNull Integer version) {
}

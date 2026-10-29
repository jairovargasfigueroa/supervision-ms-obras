package com.constructora.obras.planilla.infraestructura.web.dto;

/**
 * Cuerpo de POST /planillas/{id}/observar (docs/contratos.md). El motivo (obligatorio, 10 a 500 caracteres) lo valida
 * el dominio: si falta o no cumple, 422 MOTIVO_INVALIDO.
 */
public record ObservarPlanillaRequest(String motivo) {
}

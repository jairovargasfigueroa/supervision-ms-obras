package com.constructora.obras.planilla.infraestructura.web.dto;

/** Respuesta 202 de POST /planillas/{id}/aprobar: {"estado": "APROBANDO"} (docs/contratos.md). */
public record AprobacionResponse(String estado) {
}

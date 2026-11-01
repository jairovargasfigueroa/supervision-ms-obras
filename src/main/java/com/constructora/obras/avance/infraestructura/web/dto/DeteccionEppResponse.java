package com.constructora.obras.avance.infraestructura.web.dto;

/** 301 · Detección de casco y chaleco hecha en el celular (respuesta de POST y GET /obras/{obraId}/fotos). */
public record DeteccionEppResponse(int personas, int sinCasco, int sinChaleco, String versionModelo) {
}

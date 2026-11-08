package com.constructora.obras.avance.infraestructura.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Elemento de GET /obras/{obraId}/fotos: urlDescarga es una URL firmada (GET) que vence a los 30 minutos. deteccion
 * (301) es null si la foto no se analizó, y siempre para el cliente.
 */
public record FotoListadaResponse(UUID id, UUID partidaId, UUID registradoPor, Instant registradoEn,
        boolean publicadaCliente, String urlDescarga, DeteccionEppResponse deteccion) {
}

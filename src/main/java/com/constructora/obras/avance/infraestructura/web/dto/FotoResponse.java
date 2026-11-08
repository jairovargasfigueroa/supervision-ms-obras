package com.constructora.obras.avance.infraestructura.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Foto registrada (respuesta de POST /obras/{obraId}/fotos). deteccion es null si la foto no se analizó o se descartó
 * (301); avisos siempre está, vacía si no hay (301: DETECCION_DESCARTADA).
 */
public record FotoResponse(UUID id, UUID partidaId, UUID registradoPor, Instant registradoEn,
        boolean publicadaCliente, DeteccionEppResponse deteccion, List<String> avisos) {
}

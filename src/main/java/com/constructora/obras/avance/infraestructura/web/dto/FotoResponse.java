package com.constructora.obras.avance.infraestructura.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Foto registrada (respuesta de POST /obras/{obraId}/fotos). */
public record FotoResponse(UUID id, UUID partidaId, UUID registradoPor, Instant registradoEn,
        boolean publicadaCliente) {
}

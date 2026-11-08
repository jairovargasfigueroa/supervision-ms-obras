package com.constructora.obras.diario.infraestructura.web.dto;

import com.constructora.obras.diario.dominio.Clima;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Entrada del diario (respuesta de POST y elemento de GET /obras/{obraId}/diario). */
public record EntradaDiarioResponse(UUID id, UUID obraId, LocalDate fecha, Clima clima, String texto,
        UUID registradoPor, Instant registradoEn) {
}

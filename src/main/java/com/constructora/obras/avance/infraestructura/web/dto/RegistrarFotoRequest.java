package com.constructora.obras.avance.infraestructura.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de POST /obras/{obraId}/fotos (docs/contratos.md). tomadaEn: fecha y hora en que se tomó la foto.
 *
 * @param deteccion 301, opcional: { personas, sinCasco, sinChaleco, versionModelo }. Se recibe sin tipar para que
 *                  una detección mal formada no sea un 400: la valida el dominio y, si no sirve, la foto se registra
 *                  sin ella (DETECCION_DESCARTADA)
 */
public record RegistrarFotoRequest(
        @NotNull UUID id,
        @NotNull UUID partidaId,
        @NotBlank @Size(max = 200) String claveArchivo,
        @NotNull Instant tomadaEn,
        JsonNode deteccion) {
}

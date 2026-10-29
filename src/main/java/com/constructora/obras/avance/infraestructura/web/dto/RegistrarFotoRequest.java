package com.constructora.obras.avance.infraestructura.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Cuerpo de POST /obras/{obraId}/fotos (docs/contratos.md). tomadaEn: fecha y hora en que se tomó la foto. */
public record RegistrarFotoRequest(
        @NotNull UUID id,
        @NotNull UUID partidaId,
        @NotBlank @Size(max = 200) String claveArchivo,
        @NotNull Instant tomadaEn) {
}

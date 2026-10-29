package com.constructora.obras.avance.infraestructura.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Cuerpo de POST /obras/{obraId}/fotos/url-subida: id de la foto creado en el celular. */
public record UrlSubidaRequest(@NotNull UUID fotoId) {
}

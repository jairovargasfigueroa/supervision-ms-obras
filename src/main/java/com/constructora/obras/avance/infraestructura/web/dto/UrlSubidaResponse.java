package com.constructora.obras.avance.infraestructura.web.dto;

import java.time.Instant;

/** URL firmada (PUT, image/jpeg) y la clave con la que se registra la foto después. */
public record UrlSubidaResponse(String url, String claveArchivo, Instant expiraEn) {
}

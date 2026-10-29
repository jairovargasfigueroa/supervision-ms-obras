package com.constructora.obras.compartido.infraestructura.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * Escribe un Problem Details (RFC 9457) con "codigo" desde los filtros, donde todavía no actúa ManejadorErrores.
 * Los textos son constantes del servicio: no hace falta escaparlos.
 */
public final class RespuestaProblema {

    private RespuestaProblema() {
    }

    public static void escribir(HttpServletResponse respuesta, HttpStatus estado, String titulo, String detalle,
            String codigo) throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType("application/problem+json");
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","codigo":"%s"}"""
                .formatted(titulo, estado.value(), detalle, codigo));
    }
}

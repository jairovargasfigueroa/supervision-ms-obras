package com.constructora.obras.compartido.infraestructura.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toma el X-Request-Id que reenvía el gateway (o crea uno), lo pone en los registros y lo devuelve en la respuesta.
 * Corre antes que la seguridad para que también los 401 lleven el encabezado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroRequestId extends OncePerRequestFilter {

    public static final String ENCABEZADO = "X-Request-Id";
    public static final String CLAVE_REGISTRO = "requestId";
    private static final Pattern VALOR_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String recibido = peticion.getHeader(ENCABEZADO);
        // Un valor raro no se copia a los registros: se reemplaza por uno nuevo
        String requestId = recibido != null && VALOR_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        MDC.put(CLAVE_REGISTRO, requestId);
        respuesta.setHeader(ENCABEZADO, requestId);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(CLAVE_REGISTRO);
        }
    }
}

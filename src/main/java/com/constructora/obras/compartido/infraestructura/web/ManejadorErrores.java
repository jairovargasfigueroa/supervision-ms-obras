package com.constructora.obras.compartido.infraestructura.web;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todo error de la API sale como Problem Details con "codigo" (convenciones, «Errores REST»).
 * Las funcionalidades agregan aquí sus excepciones (ReglaNegocioException, validación con "errores").
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    /** Código genérico de cada estado cuando el error no trae uno propio. */
    private static final Map<Integer, String> CODIGO_POR_ESTADO = Map.of(
            400, "DATOS_INVALIDOS",
            401, "NO_AUTENTICADO",
            403, "SIN_PERMISO",
            404, "RUTA_NO_ENCONTRADA",
            413, "ARCHIVO_DEMASIADO_GRANDE",
            429, "DEMASIADAS_PETICIONES",
            500, "ERROR_INTERNO",
            503, "SERVICIO_NO_DISPONIBLE");

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object cuerpo, HttpHeaders encabezados,
            HttpStatusCode estado, WebRequest peticion) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(error, cuerpo, encabezados, estado, peticion);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema
                && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
            problema.setProperty("codigo", codigoGenerico(estado.value()));
        }
        return respuesta;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> errorInesperado(Exception error) {
        logger.error("Error interno", error);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado.");
        problema.setTitle("Error interno");
        problema.setProperty("codigo", "ERROR_INTERNO");
        return ResponseEntity.internalServerError().body(problema);
    }

    private static String codigoGenerico(int estado) {
        return CODIGO_POR_ESTADO.getOrDefault(estado, estado >= 500 ? "ERROR_INTERNO" : "DATOS_INVALIDOS");
    }
}

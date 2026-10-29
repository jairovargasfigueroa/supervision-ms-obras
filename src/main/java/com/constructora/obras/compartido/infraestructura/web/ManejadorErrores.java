package com.constructora.obras.compartido.infraestructura.web;

import com.constructora.obras.compartido.dominio.AlmacenArchivos.AlmacenNoDisponibleException;
import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todo error de la API sale como Problem Details con "codigo" (convenciones, «Errores REST»). Las reglas del negocio
 * (ReglaNegocioException) salen con 422, 409 o 404 según el código y, si hay, "errores" con campo y mensaje.
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

    /** Códigos de conflicto de estado o versión (docs/contratos.md): 409 en lugar de 422. */
    private static final Set<String> CODIGOS_CONFLICTO = Set.of(
            "PLANILLA_ABIERTA_EXISTE", "VERSION_DESACTUALIZADA", "PLANILLA_NO_EDITABLE", "PLANILLA_NO_PRESENTADA");

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object cuerpo,
            HttpHeaders encabezados, HttpStatusCode estado, WebRequest peticion) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(error, cuerpo, encabezados, estado, peticion);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema
                && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
            problema.setProperty("codigo", codigoGenerico(estado.value()));
        }
        return respuesta;
    }

    /** Falla la validación del cuerpo (@Valid): 400 DATOS_INVALIDOS con cada campo y su regla en "errores". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders encabezados, HttpStatusCode estado, WebRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Algunos datos no son válidos.");
        problema.setTitle("Datos inválidos");
        problema.setProperty("codigo", "DATOS_INVALIDOS");
        problema.setProperty("errores", error.getBindingResult().getFieldErrors().stream()
                .map(campo -> Map.of("campo", campo.getField(), "mensaje", mensaje(campo)))
                .toList());
        return ResponseEntity.badRequest().body(problema);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ProblemDetail> reglaNegocio(ReglaNegocioException error) {
        HttpStatus estado = estadoDe(error.codigo());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, error.getMessage());
        problema.setTitle(error.getMessage());
        problema.setProperty("codigo", error.codigo());
        if (!error.errores().isEmpty()) {
            List<Map<String, String>> errores = error.errores().stream()
                    .map(campo -> Map.of("campo", campo.campo(), "mensaje", campo.mensaje()))
                    .toList();
            problema.setProperty("errores", errores);
        }
        return ResponseEntity.status(estado).body(problema);
    }

    /** @PreAuthorize rechazó el rol (convenciones: 403 SIN_PERMISO, antes de mirar si la obra existe). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> sinPermiso(AccessDeniedException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Tu rol no permite esta acción.");
        problema.setTitle("Sin permiso");
        problema.setProperty("codigo", "SIN_PERMISO");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema);
    }

    /**
     * El almacenamiento de archivos (S3) no está configurado o no responde: 503 SERVICIO_NO_DISPONIBLE, que el
     * cliente puede reintentar más tarde (convenciones, «Errores REST»).
     */
    @ExceptionHandler(AlmacenNoDisponibleException.class)
    ResponseEntity<ProblemDetail> almacenNoDisponible(AlmacenNoDisponibleException error) {
        logger.warn("Almacenamiento de archivos no disponible: " + error.getMessage());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El almacenamiento de fotos no está disponible por el momento.");
        problema.setTitle("Servicio no disponible");
        problema.setProperty("codigo", "SERVICIO_NO_DISPONIBLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problema);
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

    /**
     * «No encontrado» (por ejemplo OBRA_NO_ENCONTRADA) → 404; conflictos → 409; SIN_PERMISO (el rol puede, pero el
     * usuario ve la obra con otro papel) → 403; el resto de reglas → 422.
     */
    static HttpStatus estadoDe(String codigo) {
        if ("SIN_PERMISO".equals(codigo)) {
            return HttpStatus.FORBIDDEN;
        }
        if (codigo.endsWith("_NO_ENCONTRADA") || codigo.endsWith("_NO_ENCONTRADO")) {
            return HttpStatus.NOT_FOUND;
        }
        if (CODIGOS_CONFLICTO.contains(codigo)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }

    private static String mensaje(FieldError campo) {
        return campo.getDefaultMessage() != null ? campo.getDefaultMessage() : "No es válido";
    }

    private static String codigoGenerico(int estado) {
        return CODIGO_POR_ESTADO.getOrDefault(estado, estado >= 500 ? "ERROR_INTERNO" : "DATOS_INVALIDOS");
    }
}

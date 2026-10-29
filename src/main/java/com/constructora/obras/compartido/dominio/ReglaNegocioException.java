package com.constructora.obras.compartido.dominio;

import java.util.List;

/**
 * Regla del negocio incumplida. ManejadorErrores la convierte en Problem Details con "codigo" (422, o 409 según el
 * código) y, si hay, "errores" con el campo y la regla concreta (convenciones, «Errores REST»).
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;
    private final transient List<ErrorCampo> errores;

    public ReglaNegocioException(String codigo, String mensaje) {
        this(codigo, mensaje, List.of());
    }

    public ReglaNegocioException(String codigo, String mensaje, List<ErrorCampo> errores) {
        super(mensaje);
        this.codigo = codigo;
        this.errores = List.copyOf(errores);
    }

    public String codigo() {
        return codigo;
    }

    public List<ErrorCampo> errores() {
        return errores;
    }

    /** Campo del pedido (por ejemplo "partidas[1].codigo") y la regla que no cumple. */
    public record ErrorCampo(String campo, String mensaje) {
    }
}

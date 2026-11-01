package com.constructora.obras.mcp;

import java.util.Objects;

/**
 * Error de negocio de una herramienta MCP (501): no es un error del protocolo; el ejecutor lo devuelve como resultado
 * con isError: true y el texto JSON {"codigo", "mensaje"}.
 */
public class ErrorHerramienta extends RuntimeException {

    public static final String ARGUMENTO_INVALIDO = "ARGUMENTO_INVALIDO";
    public static final String OBRA_NO_ENCONTRADA = "OBRA_NO_ENCONTRADA";
    public static final String ERROR_INTERNO = "ERROR_INTERNO";

    private final String codigo;

    public ErrorHerramienta(String codigo, String mensaje) {
        super(Objects.requireNonNull(mensaje, "mensaje"));
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public static ErrorHerramienta argumentoInvalido(String mensaje) {
        return new ErrorHerramienta(ARGUMENTO_INVALIDO, mensaje);
    }

    public String codigo() {
        return codigo;
    }
}

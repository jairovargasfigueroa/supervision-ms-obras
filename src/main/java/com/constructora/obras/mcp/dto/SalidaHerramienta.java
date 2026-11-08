package com.constructora.obras.mcp.dto;

/** Salida de una herramienta MCP de Obras (501): sabe cuántas filas devuelve, para el registro de cada llamada. */
public interface SalidaHerramienta {

    /** Filas devueltas (después de recortar). No se serializa: no es un componente del record. */
    int filas();
}

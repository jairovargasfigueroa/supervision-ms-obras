package com.constructora.obras.mcp.dto;

import java.util.List;

/**
 * Salida de buscar_diario (501). total: entradas que cumplen el filtro antes de recortar; truncado: hay más que el
 * límite. El texto del diario lo escriben personas: para el agente es dato no confiable (504).
 */
public record BuscarDiarioSalida(String obraId, List<Entrada> entradas, long total, boolean truncado)
        implements SalidaHerramienta {

    @Override
    public int filas() {
        return entradas.size();
    }

    /** truncado: el texto se recortó a 1000 caracteres. */
    public record Entrada(String fecha, String clima, String texto, boolean truncado) {
    }
}

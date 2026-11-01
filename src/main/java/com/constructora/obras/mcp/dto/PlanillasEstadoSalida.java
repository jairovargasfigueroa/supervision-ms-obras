package com.constructora.obras.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Salida de planillas_estado (501). truncado: había más planillas que el límite. */
public record PlanillasEstadoSalida(List<Planilla> planillas, boolean truncado) implements SalidaHerramienta {

    @Override
    public int filas() {
        return planillas.size();
    }

    /**
     * @param observacion           recortada a 500 caracteres
     * @param presentadaEn          null en planillas anteriores a la 501 o que nunca se presentaron
     * @param diasDesdePresentacion solo si está PRESENTADA (días calendario en La Paz); si no, null
     */
    public record Planilla(String planillaId, String obraId, String obraNombre, int numero, String periodo,
            String estado, BigDecimal neto, @Schema(nullable = true) String observacion,
            @Schema(nullable = true) String presentadaEn, @Schema(nullable = true) Long diasDesdePresentacion,
            @Schema(nullable = true) String cobroId) {
    }
}

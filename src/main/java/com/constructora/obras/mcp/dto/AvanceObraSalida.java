package com.constructora.obras.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Salida de avance_obra (501). De este record sale el outputSchema de la herramienta: los campos que pueden ser null
 * llevan {@code @Schema(nullable = true)} (siguen siendo obligatorios: van con null).
 *
 * @param partidas             null sin obraId (lista de obras en ejecución)
 * @param hayPlanillaEnPeriodo null si no se pidió periodo
 */
public record AvanceObraSalida(String generadoEn, List<Obra> obras,
        @Schema(nullable = true) Boolean hayPlanillaEnPeriodo, boolean truncado) implements SalidaHerramienta {

    @Override
    public int filas() {
        return obras.size();
    }

    public record Obra(String obraId, String nombre, String estado, String fechaInicio, int plazoDias,
            BigDecimal avancePct, BigDecimal plazoPct, BigDecimal diferencia, boolean enRiesgo,
            @Schema(nullable = true) UltimaPlanilla ultimaPlanilla, @Schema(nullable = true) List<Partida> partidas) {
    }

    public record UltimaPlanilla(String periodo, String estado) {
    }

    /** materialPrevisto: null si no se pidió periodo. */
    public record Partida(String partidaId, String codigo, String nombre, String unidad, BigDecimal cantidadContratada,
            BigDecimal ejecutadaAcumulada, BigDecimal avancePct,
            @Schema(nullable = true) List<MaterialPrevisto> materialPrevisto) {
    }

    public record MaterialPrevisto(String materialId, BigDecimal cantidadPrevista) {
    }
}

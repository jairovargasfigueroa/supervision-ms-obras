package com.constructora.obras.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Salida de avance_obra (501). De este record sale el outputSchema de la herramienta: los campos que pueden ser null
 * llevan {@code @Schema(nullable = true)} (siguen siendo obligatorios: van con null).
 *
 * @param partidas             null sin obraId ni periodo (lista de obras en ejecución)
 * @param hayPlanillaEnPeriodo null si no se pidió periodo o si es el lote (periodo sin obraId, 505)
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

    /**
     * Partida de la obra. materialPrevisto: null si no se pidió periodo. En el lote (505: periodo sin obraId) la
     * partida va reducida ({@link #reducida}): unidad, cantidadContratada, ejecutadaAcumulada y avancePct no van (por
     * eso no son obligatorias en el outputSchema y se omiten cuando son null).
     */
    public record Partida(String partidaId, String codigo, String nombre,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL)
            String unidad,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal cantidadContratada,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal ejecutadaAcumulada,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL)
            BigDecimal avancePct,
            @Schema(nullable = true) List<MaterialPrevisto> materialPrevisto) {

        /** Lote (505): solo partidaId, codigo, nombre y el material previsto del periodo. */
        public static Partida reducida(String partidaId, String codigo, String nombre,
                List<MaterialPrevisto> materialPrevisto) {
            return new Partida(partidaId, codigo, nombre, null, null, null, null, materialPrevisto);
        }
    }

    public record MaterialPrevisto(String materialId, BigDecimal cantidadPrevista) {
    }
}

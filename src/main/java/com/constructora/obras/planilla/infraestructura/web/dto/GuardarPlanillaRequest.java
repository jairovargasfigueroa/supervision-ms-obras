package com.constructora.obras.planilla.infraestructura.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de PUT /planillas/{id} (docs/contratos.md): versión que se editó, cantidad del periodo por partida (hasta 3
 * decimales, mayor o igual a 0) y fotos adjuntas.
 */
public record GuardarPlanillaRequest(
        @NotNull Integer version,
        @NotNull @Valid List<@NotNull Linea> lineas,
        @NotNull List<@NotNull UUID> fotoIds) {

    public record Linea(
            @NotNull UUID partidaId,
            @NotNull @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal cantidadPeriodo) {
    }
}

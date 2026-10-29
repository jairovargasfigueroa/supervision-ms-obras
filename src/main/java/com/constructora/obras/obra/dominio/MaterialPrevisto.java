package com.constructora.obras.obra.dominio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Material previsto de una partida en un periodo (docs/reglas-negocio.md, «Presupuesto por partidas»): cantidad
 * medida en la planilla del periodo × cantidad prevista por unidad, con 3 decimales.
 */
public record MaterialPrevisto(UUID partidaId, String materialId, BigDecimal cantidadPrevista) {

    public MaterialPrevisto {
        Objects.requireNonNull(partidaId, "partidaId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(cantidadPrevista, "cantidadPrevista");
    }
}

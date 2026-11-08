package com.constructora.obras.obra.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Material previsto por unidad de partida (por ejemplo 1,1 bolsas de cemento por m² de losa). El material es del
 * catálogo de Materiales: aquí solo se guarda su id.
 */
public record InsumoPrevisto(String materialId, BigDecimal cantidadPorUnidad) {

    public InsumoPrevisto {
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(cantidadPorUnidad, "cantidadPorUnidad");
        cantidadPorUnidad = cantidadPorUnidad.setScale(3, RoundingMode.HALF_UP);
    }
}

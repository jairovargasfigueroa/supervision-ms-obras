package com.constructora.obras.obra.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Partida del presupuesto: cantidad contratada × precio unitario, con su material previsto por unidad. */
public record Partida(UUID id, String codigo, String nombre, String unidad, BigDecimal cantidadContratada,
        BigDecimal precioUnitario, String meses, List<InsumoPrevisto> insumos) {

    public Partida {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(unidad, "unidad");
        Objects.requireNonNull(cantidadContratada, "cantidadContratada");
        Objects.requireNonNull(precioUnitario, "precioUnitario");
        cantidadContratada = cantidadContratada.setScale(3, RoundingMode.HALF_UP);
        precioUnitario = precioUnitario.setScale(2, RoundingMode.HALF_UP);
        insumos = insumos == null ? List.of() : List.copyOf(insumos);
    }

    /** Partida nueva, con un id creado aquí. */
    public static Partida nueva(String codigo, String nombre, String unidad, BigDecimal cantidadContratada,
            BigDecimal precioUnitario, String meses, List<InsumoPrevisto> insumos) {
        return new Partida(UUID.randomUUID(), codigo, nombre, unidad, cantidadContratada, precioUnitario, meses,
                insumos);
    }

    /** Cantidad × precio unitario, sin redondear (se redondea el total). */
    public BigDecimal importe() {
        return cantidadContratada.multiply(precioUnitario);
    }
}

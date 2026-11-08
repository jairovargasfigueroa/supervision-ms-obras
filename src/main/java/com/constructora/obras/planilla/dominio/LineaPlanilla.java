package com.constructora.obras.planilla.dominio;

import com.constructora.obras.compartido.dominio.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Línea de la planilla: cantidad ejecutada de una partida en el periodo (3 decimales) y su monto = cantidad × precio
 * unitario, redondeado a 2 decimales «mitad hacia arriba» (docs/reglas-negocio.md, «Planilla de avance»).
 */
public record LineaPlanilla(UUID partidaId, BigDecimal cantidadPeriodo, Dinero monto) {

    public LineaPlanilla {
        Objects.requireNonNull(partidaId, "partidaId");
        Objects.requireNonNull(cantidadPeriodo, "cantidadPeriodo");
        Objects.requireNonNull(monto, "monto");
        cantidadPeriodo = cantidadPeriodo.setScale(3, RoundingMode.HALF_UP);
    }

    /** Línea con la cantidad del periodo y su monto calculado con el precio unitario de la partida. */
    public static LineaPlanilla de(UUID partidaId, BigDecimal cantidadPeriodo, BigDecimal precioUnitario) {
        BigDecimal cantidad = cantidadPeriodo.setScale(3, RoundingMode.HALF_UP);
        return new LineaPlanilla(partidaId, cantidad, Dinero.de(cantidad.multiply(precioUnitario)));
    }
}

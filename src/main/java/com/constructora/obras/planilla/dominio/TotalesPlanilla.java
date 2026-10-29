package com.constructora.obras.planilla.dominio;

import com.constructora.obras.compartido.dominio.Dinero;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/**
 * Totales de la planilla (docs/reglas-negocio.md, «Planilla de avance»), cada monto con 2 decimales «mitad hacia
 * arriba» al final de su cálculo: subtotal = Σ montos de las líneas; descuento por anticipo = subtotal × anticipo %;
 * retención = subtotal × retención %; neto = subtotal − descuento por anticipo − retención.
 */
public record TotalesPlanilla(Dinero subtotal, Dinero descuentoAnticipo, Dinero retencion, Dinero neto) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static final TotalesPlanilla CERO = new TotalesPlanilla(Dinero.CERO, Dinero.CERO, Dinero.CERO, Dinero.CERO);

    public TotalesPlanilla {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(descuentoAnticipo, "descuentoAnticipo");
        Objects.requireNonNull(retencion, "retencion");
        Objects.requireNonNull(neto, "neto");
    }

    /**
     * @param montos       monto de cada línea (ya redondeado)
     * @param anticipoPct  porcentaje de anticipo del contrato (10 = 10 %)
     * @param retencionPct porcentaje de retención del contrato
     */
    public static TotalesPlanilla calcular(Collection<Dinero> montos, BigDecimal anticipoPct, BigDecimal retencionPct) {
        Dinero subtotal = montos.stream().reduce(Dinero.CERO, Dinero::sumar);
        Dinero descuento = Dinero.de(subtotal.valor().multiply(anticipoPct).divide(CIEN));
        Dinero retencion = Dinero.de(subtotal.valor().multiply(retencionPct).divide(CIEN));
        return new TotalesPlanilla(subtotal, descuento, retencion, subtotal.restar(descuento).restar(retencion));
    }
}

package com.constructora.obras.compartido.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Objects;

/**
 * Monto en bolivianos con 2 decimales y redondeo «mitad hacia arriba» (convenciones, «Nombres»).
 * Los cálculos se hacen con el valor exacto y se redondean al final con {@link #de(BigDecimal)}.
 */
public record Dinero(BigDecimal valor) implements Comparable<Dinero> {

    public static final Dinero CERO = new Dinero(BigDecimal.ZERO);

    public Dinero {
        Objects.requireNonNull(valor, "valor");
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static Dinero de(BigDecimal valor) {
        return new Dinero(valor);
    }

    public Dinero restar(Dinero otro) {
        return new Dinero(valor.subtract(otro.valor));
    }

    public Dinero abs() {
        return new Dinero(valor.abs());
    }

    public boolean esPositivo() {
        return valor.signum() > 0;
    }

    @Override
    public int compareTo(Dinero otro) {
        return valor.compareTo(otro.valor);
    }

    /** Formato que ve el usuario: «Bs 1.250.000,00» (locale es-BO, docs/diseno-visual.md). */
    public String formato() {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        simbolos.setMinusSign('-');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        return "Bs " + formato.format(valor);
    }

    @Override
    public String toString() {
        return valor.toPlainString();
    }
}

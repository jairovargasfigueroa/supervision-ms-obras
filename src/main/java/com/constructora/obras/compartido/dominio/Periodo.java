package com.constructora.obras.compartido.dominio;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Mes de un registro (convenciones, «Nombres»): se escribe AAAA-MM y se guarda como el primer día del mes (por
 * ejemplo planilla.periodo).
 */
public record Periodo(YearMonth mes) {

    private static final Pattern FORMATO = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    public Periodo {
        Objects.requireNonNull(mes, "mes");
    }

    /**
     * Lee un periodo AAAA-MM.
     *
     * @throws IllegalArgumentException si no tiene ese formato o el mes no existe
     */
    public static Periodo de(String texto) {
        if (texto == null || !FORMATO.matcher(texto).matches()) {
            throw new IllegalArgumentException("El periodo debe tener el formato AAAA-MM");
        }
        return new Periodo(YearMonth.parse(texto));
    }

    /** Periodo de una fecha (su mes). */
    public static Periodo de(LocalDate fecha) {
        return new Periodo(YearMonth.from(fecha));
    }

    /** Primer día del mes: así se guarda en la base. */
    public LocalDate primerDia() {
        return mes.atDay(1);
    }

    /** AAAA-MM. */
    @Override
    public String toString() {
        return mes.toString();
    }
}

package com.constructora.obras.semilla;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** {@code semilla.modo}: {@code cargar} inserta solo lo que no existe por id. */
public enum ModoSemilla {
    CARGAR;

    /** Lee el modo sin distinguir mayúsculas; uno desconocido es un error. */
    public static ModoSemilla de(String texto) {
        String limpio = texto == null ? "" : texto.strip().toUpperCase(Locale.ROOT);
        try {
            return valueOf(limpio);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("semilla.modo debe ser " + Arrays.stream(values())
                    .map(modo -> modo.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" o "))
                    + " (llegó «" + texto + "»)", e);
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}

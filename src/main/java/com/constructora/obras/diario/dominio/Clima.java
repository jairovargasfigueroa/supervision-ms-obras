package com.constructora.obras.diario.dominio;

import java.util.Arrays;
import java.util.Optional;

/** Clima del día en el diario de obra (103, criterio 1; valores: decisión del equipo). */
public enum Clima {
    SOLEADO,
    NUBLADO,
    LLUVIA_LEVE,
    LLUVIA_FUERTE;

    /** El clima con ese nombre exacto (SOLEADO, NUBLADO, LLUVIA_LEVE o LLUVIA_FUERTE); vacío si no es uno de ellos. */
    public static Optional<Clima> de(String valor) {
        return Arrays.stream(values()).filter(clima -> clima.name().equals(valor)).findFirst();
    }
}

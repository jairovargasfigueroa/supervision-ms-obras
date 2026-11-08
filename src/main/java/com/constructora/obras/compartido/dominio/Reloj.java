package com.constructora.obras.compartido.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Puerto de la hora: en las pruebas se usa uno fijo. */
public interface Reloj {

    /** Zona horaria del negocio (convenciones, «Nombres»). */
    ZoneId ZONA_NEGOCIO = ZoneId.of("America/La_Paz");

    Instant ahora();

    /** Fecha de hoy en la zona del negocio. */
    default LocalDate hoy() {
        return ahora().atZone(ZONA_NEGOCIO).toLocalDate();
    }
}

package com.constructora.obras.compartido.dominio;

import java.util.UUID;

/**
 * Hecho que otros servicios necesitan saber. Sus componentes son los "datos" del mensaje; el sobre común (id,
 * versión, fecha, origen) lo arma la infraestructura (convenciones, «Eventos y comandos»).
 */
public interface EventoDominio {

    /** Tipo en PascalCase y en pasado, por ejemplo "ObraRegistrada". */
    String tipo();

    /** Flujo al que pertenece el mensaje (la obra, o la saga en la 008). */
    UUID correlacionId();
}

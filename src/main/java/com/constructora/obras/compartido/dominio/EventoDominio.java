package com.constructora.obras.compartido.dominio;

import java.util.UUID;

/**
 * Hecho que otros servicios necesitan saber. Sus "datos" son, salvo que datos() diga otra cosa, los componentes del
 * propio registro; el sobre común (id, versión, fecha, origen, correlacionId) lo arma la infraestructura
 * (convenciones, «Eventos y comandos»).
 */
public interface EventoDominio {

    /** Tipo en PascalCase y en pasado, por ejemplo "ObraRegistrada". */
    String tipo();

    /** Flujo al que pertenece el mensaje (la obra, o la saga en la 008). */
    UUID correlacionId();

    /**
     * Lo que viaja en "datos". Por defecto el evento mismo; los mensajes que llevan el correlacionId como componente
     * (los de la saga) devuelven aquí solo los campos del contrato.
     */
    default Object datos() {
        return this;
    }
}

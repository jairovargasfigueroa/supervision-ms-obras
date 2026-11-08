package com.constructora.obras.compartido.infraestructura.mensajeria;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Sobre común de todo mensaje (convenciones, «Eventos y comandos»), en JSON camelCase:
 * {@code {"id","tipo","version","ocurridoEn","origen","correlacionId","datos"}}.
 */
public record Sobre(UUID id, String tipo, int version, Instant ocurridoEn, String origen, UUID correlacionId,
        JsonNode datos) {

    public static final String ORIGEN = "obras";
    public static final int VERSION = 1;
}

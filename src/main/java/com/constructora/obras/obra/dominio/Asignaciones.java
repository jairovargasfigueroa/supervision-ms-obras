package com.constructora.obras.obra.dominio;

import java.util.Objects;
import java.util.UUID;

/** Usuarios de Keycloak asignados a la obra (ids, sin validar que existan: los elige el formulario). */
public record Asignaciones(UUID residenteId, UUID maestroId, UUID almaceneroId, UUID clienteId) {

    public Asignaciones {
        Objects.requireNonNull(residenteId, "residenteId");
        Objects.requireNonNull(maestroId, "maestroId");
        Objects.requireNonNull(almaceneroId, "almaceneroId");
        Objects.requireNonNull(clienteId, "clienteId");
    }
}

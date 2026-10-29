package com.constructora.obras.compartido.dominio;

import java.util.Objects;
import java.util.Set;

/**
 * Quién hace el pedido: id de Keycloak (sub del token) y sus roles efectivos (ya aplicada la regla del azp).
 * Lo arma UsuarioActual en la infraestructura; el dominio lo usa para las reglas de visibilidad.
 */
public record Usuario(String id, Set<String> roles) {

    public Usuario {
        Objects.requireNonNull(id, "id");
        roles = Set.copyOf(roles);
    }

    public boolean tiene(String rol) {
        return roles.contains(rol);
    }
}

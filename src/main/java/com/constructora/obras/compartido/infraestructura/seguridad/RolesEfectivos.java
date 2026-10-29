package com.constructora.obras.compartido.infraestructura.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Roles que cuentan según la aplicación desde la que se inició sesión (campo azp del token), para usuarios con
 * varios roles como demo1 (convenciones, «Usuario con varios roles»).
 */
public final class RolesEfectivos {

    static final Set<String> ROLES_INTERNOS = Set.of("admin", "residente", "gerente", "maestro", "almacenero");
    private static final Set<String> APLICACIONES_INTERNAS = Set.of("erp", "app-obra");

    private RolesEfectivos() {
    }

    /**
     * azp = portal → solo cliente; azp = erp o app-obra → solo los roles internos; otro azp (clientes de servicio
     * como gateway, ia-agentes o n8n) → los roles del token tal cual.
     */
    public static List<String> de(Collection<String> roles, String azp) {
        if ("portal".equals(azp)) {
            return roles.stream().filter("cliente"::equals).toList();
        }
        if (APLICACIONES_INTERNAS.contains(azp)) {
            return roles.stream().filter(ROLES_INTERNOS::contains).toList();
        }
        return List.copyOf(roles);
    }
}

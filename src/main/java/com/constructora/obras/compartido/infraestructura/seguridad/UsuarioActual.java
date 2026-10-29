package com.constructora.obras.compartido.infraestructura.seguridad;

import com.constructora.obras.compartido.dominio.Usuario;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Usuario del token de la petición: id = sub y roles efectivos (ConvertidorRolesKeycloak ya aplicó la regla del
 * azp al crear las autoridades ROLE_&lt;rol&gt;). Los casos de uso lo reciben para validar la visibilidad por obra.
 */
@Component
public class UsuarioActual {

    private static final String PREFIJO = "ROLE_";

    public Usuario obtener() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion.getName() == null) {
            throw new IllegalStateException("No hay usuario autenticado en la petición");
        }
        return new Usuario(autenticacion.getName(), autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad != null && autoridad.startsWith(PREFIJO))
                .map(autoridad -> autoridad.substring(PREFIJO.length()))
                .collect(Collectors.toSet()));
    }
}

package com.constructora.obras.compartido.infraestructura.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convierte los roles del token de Keycloak (realm_access.roles) en autoridades ROLE_&lt;rol&gt; para
 * {@code @PreAuthorize("hasAnyRole('admin','residente')")}, aplicando antes la regla del azp (RolesEfectivos).
 * El nombre de la autenticación es el sub del token (id del usuario).
 */
public class ConvertidorRolesKeycloak implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        return new JwtAuthenticationToken(token, autoridades(token), token.getSubject());
    }

    static Collection<GrantedAuthority> autoridades(Jwt token) {
        Map<String, Object> accesoRealm = token.getClaimAsMap("realm_access");
        List<String> roles = accesoRealm != null && accesoRealm.get("roles") instanceof Collection<?> lista
                ? lista.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        return RolesEfectivos.de(roles, token.getClaimAsString("azp")).stream()
                .<GrantedAuthority>map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
    }
}

package com.constructora.obras.compartido.infraestructura.seguridad;

import com.constructora.obras.compartido.infraestructura.web.RespuestaProblema;
import jakarta.servlet.DispatcherType;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Valida el token de Keycloak en todas las rutas salvo GET /salud: firma con el JWKS del realm (se pide la primera
 * vez que llega un token), vencimiento, emisor y audiencia plataforma-api (convenciones, «Autenticación y permisos»).
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(encabezados -> encabezados.referrerPolicy(politica -> politica.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(rutas -> rutas
                        // Los errores (por ejemplo un 404) se despachan otra vez a /error: no deben pedir token de nuevo
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/salud").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ConvertidorRolesKeycloak()))
                        .authenticationEntryPoint(noAutenticado())
                        .accessDeniedHandler(sinPermiso())
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filtro) {
                                filtro.setAuthenticationFailureHandler(falloAlValidarToken());
                                return filtro;
                            }
                        }))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(noAutenticado())
                        .accessDeniedHandler(sinPermiso()));
        return http.build();
    }

    /** Solo el portal llama a Obras desde el navegador (convenciones, «Seguridad»: OBRAS_CORS_ORIGENES). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${obras.cors.origenes}") String origenes) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim)
                .filter(origen -> !origen.isEmpty()).toList());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuracion.setExposedHeaders(List.of("X-Request-Id"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }

    /**
     * Con obras.seguridad.clave-publica (solo en las pruebas) la firma se valida con esa clave en lugar del JWKS;
     * emisor, audiencia y vencimiento se validan igual.
     */
    @Bean
    JwtDecoder decodificadorJwt(@Value("${obras.seguridad.emisor}") String emisor,
            @Value("${obras.seguridad.audiencia}") String audiencia,
            @Value("${obras.seguridad.clave-publica:#{null}}") Resource clavePublica) throws IOException {
        NimbusJwtDecoder decodificador;
        if (clavePublica != null) {
            try (InputStream pem = clavePublica.getInputStream()) {
                decodificador = NimbusJwtDecoder.withPublicKey((RSAPublicKey) RsaKeyConverters.x509().convert(pem)).build();
            }
        } else {
            decodificador = NimbusJwtDecoder.withJwkSetUri(emisor + "/protocol/openid-connect/certs").build();
        }
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emisor),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(audiencia))));
        return decodificador;
    }

    private static AuthenticationEntryPoint noAutenticado() {
        return (peticion, respuesta, error) -> {
            respuesta.setHeader("WWW-Authenticate", "Bearer");
            RespuestaProblema.escribir(respuesta, HttpStatus.UNAUTHORIZED, "No autenticado",
                    "Falta el token o no es válido.", "NO_AUTENTICADO");
        };
    }

    /**
     * Token inválido → 401. Si no se pudieron obtener las claves (Keycloak caído), Spring lanza
     * AuthenticationServiceException: se responde 503 y no 401, para que el ERP no cierre la sesión del usuario.
     */
    private static AuthenticationFailureHandler falloAlValidarToken() {
        return (peticion, respuesta, error) -> {
            if (error instanceof AuthenticationServiceException) {
                RespuestaProblema.escribir(respuesta, HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                        "No se pudo validar el token: Keycloak no responde.", "SERVICIO_NO_DISPONIBLE");
            } else {
                noAutenticado().commence(peticion, respuesta, error);
            }
        };
    }

    private static AccessDeniedHandler sinPermiso() {
        return (peticion, respuesta, error) -> RespuestaProblema.escribir(respuesta, HttpStatus.FORBIDDEN,
                "Sin permiso", "Tu rol no permite esta acción.", "SIN_PERMISO");
    }
}

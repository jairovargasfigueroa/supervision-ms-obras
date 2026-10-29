package com.constructora.obras.compartido.infraestructura.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.ContenedoresPrueba;
import com.constructora.obras.soporte.TokensPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Si Keycloak no responde al pedir el JWKS, el servicio responde 503 y no 401: un 401 haría que el ERP cierre la
 * sesión del usuario por una falla del servidor (convenciones, «Autenticación y permisos»).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContenedoresPrueba.class)
@TestPropertySource(properties = "obras.seguridad.emisor=http://127.0.0.1:1/realms/supervision")
class KeycloakCaidoIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void respondeServicioNoDisponible() throws Exception {
        mvc.perform(get("/obras").header("Authorization",
                "Bearer " + TokensPrueba.valido("a0000000-0000-4000-8000-000000000003", "residente")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }
}

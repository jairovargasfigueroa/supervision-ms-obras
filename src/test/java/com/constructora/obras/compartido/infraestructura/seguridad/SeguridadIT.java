package com.constructora.obras.compartido.infraestructura.seguridad;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tarea 1.6: /salud sin token y validación del token de Keycloak en el resto. */
class SeguridadIT extends PruebaIntegracion {

    private static final String RESIDENTE1 = "a0000000-0000-4000-8000-000000000003";
    // Ninguna ruta del negocio existe todavía: con un token válido, una ruta inexistente responde 404 y no 401
    private static final String RUTA_PROTEGIDA = "/ruta-que-no-existe";

    @Autowired
    private MockMvc mvc;

    @Test
    void saludRespondeSinToken() throws Exception {
        mvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"));
    }

    @Test
    void sinTokenResponde401ConProblemDetails() throws Exception {
        esperarNoAutenticado(mvc.perform(get(RUTA_PROTEGIDA)));
    }

    @Test
    void tokenValidoEsAceptado() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + TokensPrueba.valido(RESIDENTE1, "residente")))
                .andExpect(status().isNotFound());
    }

    @Test
    void firmaDeOtraClaveEsRechazada() throws Exception {
        String token = TokensPrueba.firmadoConOtraClave(TokensPrueba.datos(RESIDENTE1, "residente").build());
        esperarNoAutenticado(conToken(token));
    }

    @Test
    void emisorDistintoEsRechazado() throws Exception {
        String token = TokensPrueba.firmar(TokensPrueba.datos(RESIDENTE1, "residente")
                .issuer("http://localhost:8080/realms/otro").build());
        esperarNoAutenticado(conToken(token));
    }

    @Test
    void audienciaDistintaEsRechazada() throws Exception {
        String token = TokensPrueba.firmar(TokensPrueba.datos(RESIDENTE1, "residente")
                .audience(List.of("account")).build());
        esperarNoAutenticado(conToken(token));
    }

    @Test
    void tokenVencidoEsRechazado() throws Exception {
        Instant antes = Instant.now().minusSeconds(3600);
        String token = TokensPrueba.firmar(TokensPrueba.datos(RESIDENTE1, "residente")
                .issueTime(Date.from(antes)).expirationTime(Date.from(antes.plusSeconds(300))).build());
        esperarNoAutenticado(conToken(token));
    }

    @Test
    void devuelveElRequestIdRecibido() throws Exception {
        mvc.perform(get("/salud").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }

    @Test
    void creaUnRequestIdSiNoVieneTambienEnUn401() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
    }

    private ResultActions conToken(String token) throws Exception {
        return mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token));
    }

    private static void esperarNoAutenticado(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}

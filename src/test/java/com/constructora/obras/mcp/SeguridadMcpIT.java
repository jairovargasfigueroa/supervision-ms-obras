package com.constructora.obras.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** 501, criterios 6 a 8: /mcp solo con el rol agente_ia de la service account de ia-agentes; REST no lo acepta. */
class SeguridadMcpIT extends PruebaIntegracion {

    private static final String GERENTE = "a0000000-0000-4000-8000-000000000002";

    @Autowired
    private MockMvc mvc;

    @Test
    void sinTokenResponde401() throws Exception {
        McpPrueba.pedir(mvc, null, "tools/list", Map.of())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        String token = TokensPrueba.firmadoConOtraClave(TokensPrueba.datos(McpPrueba.AGENTE_IA, "agente_ia")
                .claim("azp", "ia-agentes").audience(List.of(TokensPrueba.AUDIENCIA)).build());
        McpPrueba.pedir(mvc, token, "tools/list", Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void personasReciben403() throws Exception {
        for (String rol : List.of("gerente", "admin", "residente", "cliente")) {
            McpPrueba.pedir(mvc, TokensPrueba.valido(GERENTE, rol), "tools/list", Map.of())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        }
    }

    @Test
    void agenteIaDesdeOtraAplicacionNoCuenta() throws Exception {
        McpPrueba.pedir(mvc, TokensPrueba.valido(McpPrueba.AGENTE_IA, "agente_ia"), "tools/list", Map.of())
                .andExpect(status().isForbidden());
        McpPrueba.pedir(mvc, TokensPrueba.desde("gateway", McpPrueba.AGENTE_IA, "agente_ia"), "tools/list",
                Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void agenteIaEntra() throws Exception {
        McpPrueba.pedir(mvc, McpPrueba.tokenAgente(), "tools/list", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
    }

    @Test
    void agenteIaNoEntraEnNingunaRutaRest() throws Exception {
        String token = McpPrueba.tokenAgente();
        for (String ruta : List.of("/obras", "/obras/b0000000-0000-4000-8000-000000000001",
                "/obras/b0000000-0000-4000-8000-000000000001/material-previsto?periodo=2026-10",
                "/obras/b0000000-0000-4000-8000-000000000001/diario",
                "/obras/b0000000-0000-4000-8000-000000000001/planillas",
                "/obras/b0000000-0000-4000-8000-000000000001/fotos")) {
            mvc.perform(get(ruta).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        }
    }
}

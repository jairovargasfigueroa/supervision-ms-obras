package com.constructora.obras.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 501 · Servidor MCP en /mcp: HTTP «streamable» sin estado, respuestas JSON, solo herramientas. */
class ServidorMcpIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;

    @Test
    void saludaSinGuardarSesionYSoloAnunciaHerramientas() throws Exception {
        McpPrueba.pedir(mvc, McpPrueba.tokenAgente(), "initialize", Map.of(
                        "protocolVersion", "2025-11-25",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "prueba", "version", "1.0.0")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("obras"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists())
                .andExpect(jsonPath("$.result.capabilities.resources").doesNotExist())
                .andExpect(jsonPath("$.result.capabilities.prompts").doesNotExist())
                .andExpect(jsonPath("$.result.capabilities.completions").doesNotExist());
    }

    @Test
    void cadaPeticionEsIndependienteSinInitialize() throws Exception {
        McpPrueba.pedir(mvc, McpPrueba.tokenAgente(), "tools/list", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
    }

    @Test
    void getNoAbreFlujoSse() throws Exception {
        mvc.perform(get(McpPrueba.RUTA).header("Authorization", "Bearer " + McpPrueba.tokenAgente())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void noPublicaRecursosNiPrompts() throws Exception {
        McpPrueba.pedir(mvc, McpPrueba.tokenAgente(), "resources/list", Map.of())
                .andExpect(jsonPath("$.error.code").exists());
        McpPrueba.pedir(mvc, McpPrueba.tokenAgente(), "prompts/list", Map.of())
                .andExpect(jsonPath("$.error.code").exists());
    }
}

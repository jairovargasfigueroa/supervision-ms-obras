package com.constructora.obras.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;

/** 501, criterio 14: cada llamada deja una línea JSON con el X-Request-Id que manda el agente, sin el token. */
@ExtendWith(OutputCaptureExtension.class)
class RegistroMcpIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;

    @Test
    void laLlamadaQuedaRegistradaConElIdDeLaEjecucion(CapturedOutput salida) throws Exception {
        String token = McpPrueba.tokenAgente();
        String ejecucion = "ejecucion-" + UUID.randomUUID();
        String obra = UUID.randomUUID().toString();

        McpPrueba.pedir(mvc, token, "tools/call", Map.of("name", "buscar_diario",
                        "arguments", Map.of("obraId", obra, "limite", 5)), ejecucion)
                .andExpect(header().string("X-Request-Id", ejecucion));

        String linea = salida.getOut().lines().filter(texto -> texto.contains("\"requestId\":\"" + ejecucion + "\""))
                .findFirst().orElseThrow(() -> new AssertionError("No se registró la llamada"));
        Map<String, Object> registro = McpPrueba.json().readValue(linea.substring(linea.indexOf('{')),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(registro).containsEntry("herramienta", "buscar_diario")
                .containsEntry("argumentos", Map.of("obraId", obra, "limite", 5))
                .containsEntry("filas", 0).containsKey("duracionMs").containsEntry("error", "OBRA_NO_ENCONTRADA");
        assertThat(salida.getOut()).doesNotContain(token);
    }
}

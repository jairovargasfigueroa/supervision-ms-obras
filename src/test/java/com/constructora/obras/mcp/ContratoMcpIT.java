package com.constructora.obras.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.soporte.McpPrueba;
import com.constructora.obras.soporte.PruebaIntegracion;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * 501, criterios 2, 3, 5 y 9: tools/list devuelve exactamente las herramientas del contrato (docs/contratos.md,
 * copiadas en mcp/herramientas-obras.json) con sus esquemas estrictos y la anotación de solo lectura, y los
 * argumentos que no cumplen el esquema devuelven ARGUMENTO_INVALIDO en las tres herramientas.
 */
@SuppressWarnings("unchecked")
class ContratoMcpIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;

    @Test
    void laListaEsExactamenteLaDelContrato() throws Exception {
        Map<String, Object> respuesta = (Map<String, Object>) McpPrueba.cuerpo(
                McpPrueba.pedir(mvc, McpPrueba.tokenAgente(), "tools/list", Map.of())).get("result");
        JsonNode publicadas = McpPrueba.json().valueToTree(respuesta.get("tools"));
        JsonNode contrato;
        try (InputStream archivo = getClass().getResourceAsStream("/mcp/herramientas-obras.json")) {
            contrato = McpPrueba.json().readTree(archivo);
        }

        assertThat(publicadas).isEqualTo(contrato);
        assertThat(publicadas.valueStream().map(herramienta -> herramienta.get("name").asString()).toList())
                .containsExactlyInAnyOrder("avance_obra", "buscar_diario", "planillas_estado")
                .doesNotContain("asistencia_obra");
        publicadas.valueStream().forEach(herramienta -> {
            assertThat(herramienta.at("/annotations/readOnlyHint").asBoolean()).isTrue();
            assertThat(herramienta.at("/annotations/openWorldHint").asBoolean()).isFalse();
            assertThat(herramienta.at("/inputSchema/additionalProperties").asBoolean(true)).isFalse();
            assertThat(herramienta.at("/outputSchema/additionalProperties").asBoolean(true)).isFalse();
            assertThat(herramienta.get("description").asString()).startsWith("Devuelve");
        });
    }

    @Test
    void argumentosMaliciososOFueraDelEsquema() throws Exception {
        String obra = UUID.randomUUID().toString();
        Map<String, List<Map<String, Object>>> casos = Map.of(
                "avance_obra", List.of(
                        Map.of("obraId", "no-es-un-uuid"),
                        Map.of("obraId", "x' OR 1=1"),
                        Map.of("obraId", obra, "periodo", "2027-13"),
                        Map.of("obraId", obra, "otro", 1),
                        Map.of("obraId", 123)),
                "buscar_diario", List.of(
                        Map.of(),
                        Map.of("obraId", "x' OR 1=1"),
                        Map.of("obraId", obra, "limite", 1000),
                        Map.of("obraId", obra, "limite", 0),
                        Map.of("obraId", obra, "texto", "x"),
                        Map.of("obraId", obra, "texto", "y".repeat(51)),
                        Map.of("obraId", obra, "desde", "08/03/2027"),
                        Map.of("obraId", obra, "sql", "drop table obra")),
                "planillas_estado", List.of(
                        Map.of("obraId", "x' OR 1=1"),
                        Map.of("estados", List.of("APROBADA'; delete from planilla; --")),
                        Map.of("estados", List.of("PRESENTADA", "PRESENTADA")),
                        Map.of("desdePeriodo", "2027-13"),
                        Map.of("limite", 1000),
                        Map.of("limite", "30"),
                        Map.of("otro", true)));
        for (var caso : casos.entrySet()) {
            for (Map<String, Object> argumentos : caso.getValue()) {
                Map<String, Object> error = McpPrueba.error(McpPrueba.llamar(mvc, caso.getKey(), new HashMap<>(argumentos)));
                assertThat(error).as(caso.getKey() + " " + argumentos).containsEntry("codigo", "ARGUMENTO_INVALIDO");
                assertThat((String) error.get("mensaje")).isNotBlank();
            }
        }
    }

    @Test
    void unaHerramientaQueNoExisteEsUnErrorDelProtocolo() throws Exception {
        Map<String, Object> respuesta = McpPrueba.cuerpo(McpPrueba.llamar(mvc, "asistencia_obra", Map.of()));
        assertThat(respuesta).containsKey("error").doesNotContainKey("result");
    }
}

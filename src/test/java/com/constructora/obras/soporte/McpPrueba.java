package com.constructora.obras.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente MCP mínimo para las pruebas (501): un mensaje JSON-RPC por POST /mcp, como lo manda el SDK (Accept con
 * application/json y text/event-stream). Sin estado: cada llamada es independiente.
 */
public final class McpPrueba {

    public static final String RUTA = "/mcp";
    /** Token de la service account de ia-agentes: rol agente_ia y azp ia-agentes (501). */
    public static final String AGENTE_IA = "a0000000-0000-4000-8000-0000000000a1";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private McpPrueba() {
    }

    public static String tokenAgente() {
        return TokensPrueba.desde("ia-agentes", AGENTE_IA, "agente_ia");
    }

    public static ResultActions pedir(MockMvc mvc, String token, String metodo, Map<String, Object> parametros)
            throws Exception {
        return pedir(mvc, token, metodo, parametros, null);
    }

    public static ResultActions pedir(MockMvc mvc, String token, String metodo, Map<String, Object> parametros,
            String requestId) throws Exception {
        var pedido = post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", UUID.randomUUID().toString(),
                        "method", metodo, "params", parametros)));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        if (requestId != null) {
            pedido.header("X-Request-Id", requestId);
        }
        return mvc.perform(pedido);
    }

    /** tools/call con el token de agente_ia. */
    public static ResultActions llamar(MockMvc mvc, String herramienta, Map<String, Object> argumentos)
            throws Exception {
        return pedir(mvc, tokenAgente(), "tools/call", Map.of("name", herramienta, "arguments", argumentos));
    }

    /** Lee el cuerpo de la respuesta como JSON. */
    public static Map<String, Object> cuerpo(ResultActions resultado) throws Exception {
        return JSON.readValue(resultado.andReturn().getResponse().getContentAsString(),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
    }

    /** structuredContent de un tools/call que salió bien (falla si vino con isError). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> salida(ResultActions resultado) throws Exception {
        Map<String, Object> respuesta = (Map<String, Object>) cuerpo(resultado).get("result");
        if (Boolean.TRUE.equals(respuesta.get("isError"))) {
            throw new AssertionError("La herramienta devolvió un error: " + respuesta.get("content"));
        }
        return (Map<String, Object>) respuesta.get("structuredContent");
    }

    /** {"codigo", "mensaje"} de un tools/call con isError: true (falla si salió bien). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> error(ResultActions resultado) throws Exception {
        Map<String, Object> respuesta = (Map<String, Object>) cuerpo(resultado).get("result");
        if (!Boolean.TRUE.equals(respuesta.get("isError"))) {
            throw new AssertionError("Se esperaba un error y la herramienta respondió " + respuesta);
        }
        if (respuesta.get("structuredContent") != null) {
            throw new AssertionError("Un error no lleva structuredContent");
        }
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) respuesta.get("content");
        return JSON.readValue((String) contenido.getFirst().get("text"),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
    }

    public static JsonMapper json() {
        return JSON;
    }
}

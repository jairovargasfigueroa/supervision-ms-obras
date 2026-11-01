package com.constructora.obras.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.tool.SyncStatelessMcpToolProvider;
import org.springframework.ai.mcp.customizer.McpStatelessSyncServerCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra las herramientas MCP de Obras (501). Spring AI arma cada herramienta desde su método @McpTool (nombre,
 * descripción, anotaciones y outputSchema desde el record de salida); aquí se le pone el esquema de entrada estricto
 * (EsquemasEntradaMcp), se cierra el de salida (additionalProperties: false en cada objeto) y la llamada pasa por
 * EjecutorHerramientasMcp. El escáner de anotaciones de Spring AI está apagado (application.yaml) para que las
 * herramientas no se registren dos veces.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ConfiguracionMcp {

    @Bean
    List<SyncToolSpecification> especificacionesMcpObras(HerramientasMcpObras herramientas,
            EjecutorHerramientasMcp ejecutor) {
        Map<String, Method> metodos = new LinkedHashMap<>();
        Arrays.stream(HerramientasMcpObras.class.getDeclaredMethods())
                .filter(metodo -> metodo.isAnnotationPresent(McpTool.class))
                .forEach(metodo -> metodos.put(metodo.getAnnotation(McpTool.class).name(), metodo));
        return new SyncStatelessMcpToolProvider(List.of(herramientas)).getToolSpecifications().stream()
                .map(especificacion -> {
                    McpSchema.Tool generada = especificacion.tool();
                    McpSchema.Tool herramienta = McpSchema.Tool.builder(generada.name(),
                                    EsquemasEntradaMcp.de(generada.name()))
                            .title(generada.title())
                            .description(generada.description())
                            .outputSchema(cerrar(generada.outputSchema()))
                            .annotations(generada.annotations())
                            .build();
                    Method metodo = metodos.get(generada.name());
                    return SyncToolSpecification.builder()
                            .tool(herramienta)
                            .callHandler((contexto, pedido) -> ejecutor.ejecutar(herramienta, metodo, herramientas,
                                    pedido))
                            .build();
                })
                .toList();
    }

    /**
     * El SDK no valida la entrada por su cuenta: lo hace EjecutorHerramientasMcp con el mismo esquema, para responder
     * el error con el formato del contrato ({"codigo": "ARGUMENTO_INVALIDO", ...}).
     */
    @Bean
    McpStatelessSyncServerCustomizer validacionEnElEjecutor() {
        return servidor -> servidor.validateToolInputs(false);
    }

    /** additionalProperties: false en cada objeto del esquema (Spring AI genera el de salida abierto). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> cerrar(Map<String, Object> esquema) {
        if (esquema == null) {
            return null;
        }
        Map<String, Object> cerrado = new LinkedHashMap<>();
        esquema.forEach((clave, valor) -> cerrado.put(clave, cerrarValor(valor)));
        if (cerrado.containsKey("properties")) {
            cerrado.put("additionalProperties", false);
        }
        return cerrado;
    }

    @SuppressWarnings("unchecked")
    private static Object cerrarValor(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            return cerrar((Map<String, Object>) mapa);
        }
        if (valor instanceof List<?> lista) {
            return lista.stream().map(ConfiguracionMcp::cerrarValor).toList();
        }
        return valor;
    }
}

package com.constructora.obras.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.infraestructura.web.FiltroRequestId;
import com.constructora.obras.mcp.dto.SalidaHerramienta;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 501 · EjecutorHerramientasMcp sin Spring: validación contra el esquema antes de llamar, errores como resultado
 * (ARGUMENTO_INVALIDO, OBRA_NO_ENCONTRADA, ERROR_INTERNO sin detalles internos), transacción de solo lectura de 5 s,
 * structuredContent igual al texto y una línea JSON de registro por llamada con el X-Request-Id.
 */
@ExtendWith(OutputCaptureExtension.class)
class EjecutorHerramientasMcpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<TransactionDefinition> transacciones = new ArrayList<>();
    private final EjecutorHerramientasMcp ejecutor = new EjecutorHerramientasMcp(new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definicion) {
            transacciones.add(definicion);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus estado) {
        }

        @Override
        public void rollback(TransactionStatus estado) {
        }
    });
    private final Falsa falsa = new Falsa();
    private final McpSchema.Tool herramienta = McpSchema.Tool.builder("falsa", Map.of(
            "type", "object",
            "properties", Map.of("valor", Map.of("type", "integer", "minimum", 1, "maximum", 10)),
            "required", List.of("valor"),
            "additionalProperties", false)).build();

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    void salidaEnStructuredContentYElMismoJsonComoTexto() throws Exception {
        CallToolResult resultado = llamar(Map.of("valor", 3));

        assertThat(resultado.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(resultado.structuredContent()).isEqualTo(Map.of("valor", 3, "eco", "tres"));
        assertThat(JSON.readValue(texto(resultado), new TypeReference<Map<String, Object>>() {
        })).isEqualTo(resultado.structuredContent());
        assertThat(transacciones).singleElement().satisfies(definicion -> {
            assertThat(definicion.isReadOnly()).isTrue();
            assertThat(definicion.getTimeout()).isEqualTo(5);
        });
    }

    @Test
    void argumentosFueraDelEsquemaNoLleganAlMetodo() throws Exception {
        for (Map<String, Object> argumentos : List.<Map<String, Object>>of(Map.of(), Map.of("valor", 1000),
                Map.of("valor", "x' OR 1=1"), Map.of("valor", 2, "otro", true))) {
            assertThat(codigo(llamar(argumentos))).isEqualTo("ARGUMENTO_INVALIDO");
        }
        assertThat(falsa.llamadas).isZero();
        assertThat(transacciones).isEmpty();
    }

    @Test
    void erroresDeNegocioComoResultado() throws Exception {
        assertThat(codigo(llamar(Map.of("valor", 7)))).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(codigo(llamar(Map.of("valor", 8)))).isEqualTo("OBRA_NO_ENCONTRADA");
    }

    @Test
    void unaFallaInesperadaEsErrorInternoSinDetalles() throws Exception {
        for (int valor : new int[] {9, 10}) {
            CallToolResult resultado = llamar(Map.of("valor", valor));
            Map<String, Object> error = error(resultado);
            assertThat(error).containsEntry("codigo", "ERROR_INTERNO")
                    .containsEntry("mensaje", "No se pudo completar la consulta.");
            assertThat(texto(resultado)).doesNotContain("secreto");
        }
    }

    @Test
    void registraUnaLineaJsonPorLlamadaConElRequestId(CapturedOutput salida) throws Exception {
        MDC.put(FiltroRequestId.CLAVE_REGISTRO, "ejecucion-123");
        llamar(Map.of("valor", 3));
        llamar(Map.of("valor", 8));

        List<Map<String, Object>> lineas = salida.getOut().lines()
                .filter(linea -> linea.contains("{\"herramienta\":\"falsa\""))
                .map(linea -> JSON.readValue(linea.substring(linea.indexOf('{')),
                        new TypeReference<Map<String, Object>>() {
                        }))
                .toList();
        assertThat(lineas).hasSize(2);
        assertThat(lineas.getFirst()).containsEntry("argumentos", Map.of("valor", 3)).containsEntry("filas", 1)
                .containsEntry("requestId", "ejecucion-123").containsKey("duracionMs").doesNotContainKey("error");
        assertThat(lineas.get(1)).containsEntry("filas", 0).containsEntry("error", "OBRA_NO_ENCONTRADA");
    }

    private CallToolResult llamar(Map<String, Object> argumentos) throws Exception {
        Method metodo = Falsa.class.getMethod("ejecutar", Integer.class);
        return ejecutor.ejecutar(herramienta, metodo, falsa, new McpSchema.CallToolRequest("falsa", argumentos));
    }

    private static String texto(CallToolResult resultado) {
        return ((TextContent) resultado.content().getFirst()).text();
    }

    private static Map<String, Object> error(CallToolResult resultado) {
        assertThat(resultado.isError()).isTrue();
        assertThat(resultado.structuredContent()).isNull();
        return JSON.readValue(texto(resultado), new TypeReference<Map<String, Object>>() {
        });
    }

    private static Object codigo(CallToolResult resultado) {
        return error(resultado).get("codigo");
    }

    /** Herramienta de prueba: 7 → ARGUMENTO_INVALIDO, 8 → OBRA_NO_ENCONTRADA, 9 y 10 → fallas inesperadas. */
    public static class Falsa {

        int llamadas;

        public Salida ejecutar(Integer valor) {
            llamadas++;
            return switch (valor) {
                case 7 -> throw ErrorHerramienta.argumentoInvalido("valor no permitido");
                case 8 -> throw new ReglaNegocioException("OBRA_NO_ENCONTRADA", "Obra no encontrada");
                case 9 -> throw new IllegalStateException("detalle secreto de la base");
                case 10 -> throw new ReglaNegocioException("SIN_PERMISO", "secreto");
                default -> new Salida(valor, "tres");
            };
        }
    }

    public record Salida(int valor, String eco) implements SalidaHerramienta {

        @Override
        public int filas() {
            return 1;
        }
    }
}

package com.constructora.obras.mcp;

import com.constructora.obras.compartido.dominio.ReglaNegocioException;
import com.constructora.obras.compartido.infraestructura.web.FiltroRequestId;
import com.constructora.obras.mcp.dto.SalidaHerramienta;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Corre una herramienta MCP (501, «Decisiones comunes»): valida los argumentos contra su esquema de entrada (si no lo
 * cumplen, ARGUMENTO_INVALIDO sin consultar la base), llama al método @McpTool en una transacción de solo lectura con
 * un tiempo máximo de 5 s y arma el resultado: structuredContent con la salida y el mismo JSON como texto. Los errores
 * de negocio vuelven como resultado con isError: true y el texto {"codigo", "mensaje"}.
 */
@Component
public class EjecutorHerramientasMcp {

    /** Tiempo máximo de consulta de una herramienta (501). */
    static final int TIEMPO_MAXIMO_S = 5;
    private static final int LARGO_MAXIMO_MENSAJE = 500;
    private static final String PREFIJO_VALIDACION = "Validation failed: JSON schema validation errors: ";
    private static final int LARGO_MAXIMO_ARGUMENTOS = 2000;
    private static final Logger log = LoggerFactory.getLogger(EjecutorHerramientasMcp.class);
    /** Registro de las llamadas (docs/contratos.md, «Herramientas MCP», «Registro»). */
    private static final Logger registro = LoggerFactory.getLogger("McpObras");

    private final JsonMapper json = JsonMapper.builder().build();
    private final JsonSchemaValidator validador = new DefaultJsonSchemaValidator(json);
    private final TransactionTemplate lectura;

    public EjecutorHerramientasMcp(PlatformTransactionManager transacciones) {
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.lectura.setTimeout(TIEMPO_MAXIMO_S);
    }

    CallToolResult ejecutar(McpSchema.Tool herramienta, Method metodo, Object destino,
            McpSchema.CallToolRequest pedido) {
        long inicio = System.nanoTime();
        Map<String, Object> argumentos = pedido.arguments() == null ? Map.of() : pedido.arguments();
        int filas = 0;
        String codigoError = null;
        try {
            validar(herramienta, argumentos);
            Object[] valores = valores(metodo, argumentos);
            SalidaHerramienta salida = lectura.execute(estado -> invocar(metodo, destino, valores));
            filas = salida.filas();
            return exito(salida);
        } catch (ErrorHerramienta e) {
            codigoError = e.codigo();
            return error(e.codigo(), e.getMessage());
        } catch (ReglaNegocioException e) {
            if (ErrorHerramienta.OBRA_NO_ENCONTRADA.equals(e.codigo())) {
                codigoError = ErrorHerramienta.OBRA_NO_ENCONTRADA;
                return error(codigoError, "Obra no encontrada.");
            }
            log.error("Regla de negocio inesperada en la herramienta {}: {}", herramienta.name(), e.codigo(), e);
            codigoError = ErrorHerramienta.ERROR_INTERNO;
            return error(codigoError, "No se pudo completar la consulta.");
        } catch (RuntimeException e) {
            // Incluye la consulta que pasó de 5 s: el detalle queda en el registro, no en la respuesta
            log.error("Falló la herramienta {}", herramienta.name(), e);
            codigoError = ErrorHerramienta.ERROR_INTERNO;
            return error(codigoError, "No se pudo completar la consulta.");
        } finally {
            registrar(herramienta.name(), argumentos, filas, (System.nanoTime() - inicio) / 1_000_000, codigoError);
        }
    }

    /**
     * Una línea JSON por llamada (501, criterio 14): herramienta, argumentos, filas devueltas, duración, X-Request-Id
     * (lo pone FiltroRequestId en el MDC; el agente manda el id de su ejecución) y, si falló, el código. Nunca el
     * token. Unos argumentos muy largos (que no pasan el esquema) no se copian enteros.
     */
    private void registrar(String herramienta, Map<String, Object> argumentos, int filas, long duracionMs,
            String codigoError) {
        Map<String, Object> linea = new LinkedHashMap<>();
        linea.put("herramienta", herramienta);
        String copia = json.writeValueAsString(argumentos);
        linea.put("argumentos", copia.length() <= LARGO_MAXIMO_ARGUMENTOS ? argumentos
                : "(recortados: " + copia.length() + " caracteres)");
        linea.put("filas", filas);
        linea.put("duracionMs", duracionMs);
        linea.put("requestId", MDC.get(FiltroRequestId.CLAVE_REGISTRO));
        if (codigoError != null) {
            linea.put("error", codigoError);
        }
        registro.info(json.writeValueAsString(linea));
    }

    private void validar(McpSchema.Tool herramienta, Map<String, Object> argumentos) {
        JsonSchemaValidator.ValidationResponse validacion = validador.validate(herramienta.inputSchema(), argumentos);
        if (!validacion.valid()) {
            String detalle = validacion.errorMessage() == null ? "" : validacion.errorMessage();
            if (detalle.startsWith(PREFIJO_VALIDACION)) {
                detalle = detalle.substring(PREFIJO_VALIDACION.length());
            }
            throw ErrorHerramienta.argumentoInvalido("Los argumentos no cumplen el esquema de la herramienta: "
                    + recortar(detalle));
        }
    }

    /** Argumentos por nombre de parámetro; ya cumplen el esquema, así que la conversión no falla. */
    private Object[] valores(Method metodo, Map<String, Object> argumentos) {
        Parameter[] parametros = metodo.getParameters();
        Object[] valores = new Object[parametros.length];
        for (int i = 0; i < parametros.length; i++) {
            Object crudo = argumentos.get(parametros[i].getName());
            valores[i] = crudo == null ? null
                    : json.convertValue(crudo, json.constructType(parametros[i].getParameterizedType()));
        }
        return valores;
    }

    private static SalidaHerramienta invocar(Method metodo, Object destino, Object[] valores) {
        try {
            return (SalidaHerramienta) metodo.invoke(destino, valores);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private CallToolResult exito(SalidaHerramienta salida) {
        Map<String, Object> estructurada = json.convertValue(salida, new TypeReference<Map<String, Object>>() {
        });
        return CallToolResult.builder()
                .structuredContent(estructurada)
                .addTextContent(json.writeValueAsString(estructurada))
                .build();
    }

    private CallToolResult error(String codigo, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("mensaje", mensaje);
        return CallToolResult.builder().isError(true).addTextContent(json.writeValueAsString(cuerpo)).build();
    }

    private static String recortar(String texto) {
        return texto.length() > LARGO_MAXIMO_MENSAJE ? texto.substring(0, LARGO_MAXIMO_MENSAJE) + "…" : texto;
    }
}

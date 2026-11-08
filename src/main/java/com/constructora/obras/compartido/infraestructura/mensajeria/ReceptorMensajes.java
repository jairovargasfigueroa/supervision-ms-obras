package com.constructora.obras.compartido.infraestructura.mensajeria;

import com.constructora.obras.compartido.dominio.Reloj;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consume la cola obras (convenciones, «Eventos y comandos»):
 * <ul>
 * <li>Inbox: el id del mensaje se guarda en mensaje_procesado en la misma transacción que su efecto; si ya estaba,
 * se confirma sin hacer nada.</li>
 * <li>Si procesar falla, se vuelve a publicar en la cola obras (exchange por defecto, cuerpo sin tocar) con
 * x-intentos + 1 y se confirma el original; al tercer fallo va a plataforma.fallidos.</li>
 * <li>Un sobre que no es JSON, con versión distinta de 1, con datos incompletos o de un tipo sin consumidor va
 * directo a plataforma.fallidos.</li>
 * </ul>
 * Encabezados del reintento y de fallidos: x-intentos, x-error, x-cola-origen y x-clave-origen (docs/contratos.md).
 */
@Component
public class ReceptorMensajes {

    public static final String X_INTENTOS = "x-intentos";
    public static final String X_ERROR = "x-error";
    public static final String X_COLA_ORIGEN = "x-cola-origen";
    public static final String X_CLAVE_ORIGEN = "x-clave-origen";
    static final int MAXIMO_INTENTOS = 3;
    private static final int LARGO_MAXIMO_ERROR = 500;
    private static final long ESPERA_CONFIRMACION_MS = 5000;
    private static final Logger log = LoggerFactory.getLogger(ReceptorMensajes.class);

    private final Map<String, ManejadorMensaje> manejadores;
    private final MensajeProcesadoJpaRepository procesados;
    private final TransactionTemplate transaccion;
    private final RabbitTemplate rabbit;
    private final JsonMapper json;
    private final Reloj reloj;

    public ReceptorMensajes(List<ManejadorMensaje> manejadores, MensajeProcesadoJpaRepository procesados,
            TransactionTemplate transaccion, RabbitTemplate rabbit, JsonMapper json, Reloj reloj) {
        this.manejadores = manejadores.stream()
                .collect(Collectors.toUnmodifiableMap(ManejadorMensaje::tipo, Function.identity()));
        this.procesados = procesados;
        this.transaccion = transaccion;
        this.rabbit = rabbit;
        this.json = json;
        this.reloj = reloj;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA)
    public void recibir(Message mensaje) {
        int intentos = intentosPrevios(mensaje) + 1;
        try {
            Sobre sobre = leerSobre(mensaje.getBody());
            ManejadorMensaje manejador = manejadores.get(sobre.tipo());
            if (manejador == null) {
                throw new MensajeInvalidoException("Tipo sin consumidor en la cola obras: " + sobre.tipo());
            }
            procesarUnaVez(sobre, manejador);
        } catch (MensajeInvalidoException error) {
            log.warn("Mensaje inválido {}, va a {}: {}", mensaje.getMessageProperties().getMessageId(),
                    ConfiguracionRabbit.COLA_FALLIDOS, error.getMessage());
            reenviar(mensaje, ConfiguracionRabbit.COLA_FALLIDOS, intentos, error);
        } catch (RuntimeException error) {
            boolean agotado = intentos >= MAXIMO_INTENTOS;
            log.warn("Falló el mensaje {} (intento {} de {}){}: {}", mensaje.getMessageProperties().getMessageId(),
                    intentos, MAXIMO_INTENTOS, agotado ? ", va a " + ConfiguracionRabbit.COLA_FALLIDOS : "",
                    error.toString());
            reenviar(mensaje, agotado ? ConfiguracionRabbit.COLA_FALLIDOS : ConfiguracionRabbit.COLA, intentos, error);
        }
    }

    private void procesarUnaVez(Sobre sobre, ManejadorMensaje manejador) {
        transaccion.executeWithoutResult(estado -> {
            if (procesados.existsById(sobre.id())) {
                log.info("Mensaje repetido {} {}: ya estaba procesado", sobre.tipo(), sobre.id());
                return;
            }
            procesados.saveAndFlush(new MensajeProcesadoEntity(sobre.id(), reloj.ahora()));
            manejador.procesar(sobre);
            log.info("Procesado {} {}", sobre.tipo(), sobre.id());
        });
    }

    /** Sobre común; si no es JSON, la versión no es 1 o falta id, tipo o datos: MensajeInvalidoException. */
    Sobre leerSobre(byte[] cuerpo) {
        JsonNode raiz;
        try {
            raiz = json.readTree(new String(cuerpo, StandardCharsets.UTF_8));
        } catch (JacksonException error) {
            throw new MensajeInvalidoException("El cuerpo no es JSON");
        }
        if (raiz == null || !raiz.isObject()) {
            throw new MensajeInvalidoException("El cuerpo no es un objeto JSON");
        }
        JsonNode version = raiz.get("version");
        if (version == null || !version.isIntegralNumber() || version.asInt() != Sobre.VERSION) {
            throw new MensajeInvalidoException("Versión del sobre no soportada: " + version);
        }
        String tipo = texto(raiz, "tipo");
        JsonNode datos = raiz.get("datos");
        if (tipo == null || tipo.isBlank() || datos == null || !datos.isObject()) {
            throw new MensajeInvalidoException("Al sobre le faltan tipo o datos");
        }
        try {
            String ocurridoEn = texto(raiz, "ocurridoEn");
            String correlacionId = texto(raiz, "correlacionId");
            return new Sobre(UUID.fromString(requerido(texto(raiz, "id"), "id")), tipo, Sobre.VERSION,
                    ocurridoEn == null ? null : Instant.parse(ocurridoEn), texto(raiz, "origen"),
                    correlacionId == null ? null : UUID.fromString(correlacionId), datos);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException error) {
            throw new MensajeInvalidoException("Sobre con un campo mal formado: " + error.getMessage());
        }
    }

    /**
     * Publica el mismo cuerpo, sin tocarlo, por el exchange por defecto en la cola indicada, y espera la
     * confirmación antes de que se confirme el original. Si RabbitMQ no responde, la excepción hace que el original
     * vuelva a la cola.
     */
    private void reenviar(Message original, String cola, int intentos, Exception error) {
        MessageProperties origen = original.getMessageProperties();
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(origen.getContentType());
        propiedades.setContentEncoding(origen.getContentEncoding());
        propiedades.setMessageId(origen.getMessageId());
        propiedades.setCorrelationId(origen.getCorrelationId());
        propiedades.setType(origen.getType());
        propiedades.setTimestamp(origen.getTimestamp());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Map<String, Object> encabezados = new HashMap<>(origen.getHeaders());
        String claveOrigen = encabezados.get(X_CLAVE_ORIGEN) instanceof Object clave
                ? clave.toString()
                : origen.getReceivedRoutingKey();
        encabezados.put(X_INTENTOS, intentos);
        encabezados.put(X_ERROR, textoError(error));
        encabezados.put(X_COLA_ORIGEN, ConfiguracionRabbit.COLA);
        encabezados.put(X_CLAVE_ORIGEN, claveOrigen);
        encabezados.forEach(propiedades::setHeader);
        Message copia = new Message(original.getBody(), propiedades);
        rabbit.invoke(operaciones -> {
            operaciones.send("", cola, copia);
            operaciones.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
            return null;
        });
    }

    private static int intentosPrevios(Message mensaje) {
        Object valor = mensaje.getMessageProperties().getHeaders().get(X_INTENTOS);
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        if (valor != null) {
            try {
                return Integer.parseInt(valor.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** Clase y mensaje de la excepción, recortados: no lleva el cuerpo del mensaje ni datos sensibles. */
    private static String textoError(Exception error) {
        String texto = error.getClass().getSimpleName() + ": " + error.getMessage();
        return texto.length() > LARGO_MAXIMO_ERROR ? texto.substring(0, LARGO_MAXIMO_ERROR) : texto;
    }

    private static String texto(JsonNode raiz, String campo) {
        JsonNode valor = raiz.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    private static String requerido(String valor, String campo) {
        if (valor == null) {
            throw new MensajeInvalidoException("Al sobre le falta " + campo);
        }
        return valor;
    }
}

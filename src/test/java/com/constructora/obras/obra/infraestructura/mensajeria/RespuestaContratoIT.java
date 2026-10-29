package com.constructora.obras.obra.infraestructura.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.constructora.obras.soporte.Esperar;
import com.constructora.obras.soporte.ObrasPrueba;
import com.constructora.obras.soporte.ObrasPrueba.Equipo;
import com.constructora.obras.soporte.PruebaIntegracion;
import com.constructora.obras.soporte.TokensPrueba;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tarea 7: consumo de ContratoRegistrado y ContratoRechazado desde la cola obras, idempotente con mensaje_procesado,
 * con reintentos y la cola plataforma.fallidos (002, criterios 8 y 9).
 */
class RespuestaContratoIT extends PruebaIntegracion {

    private static final String REGISTRADO = "evento.finanzas.ContratoRegistrado";
    private static final String RECHAZADO = "evento.finanzas.ContratoRechazado";
    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contratoRegistradoPasaLaObraAEnEjecucion() throws Exception {
        UUID obraId = ObrasPrueba.registrar(mvc, "Obra con contrato", Equipo.nuevo());
        UUID contratoId = UUID.randomUUID();

        publicar(REGISTRADO, sobre(UUID.randomUUID(), "ContratoRegistrado",
                "{\"obraId\": \"%s\", \"contratoId\": \"%s\"}".formatted(obraId, contratoId)));

        Esperar.hasta(ESPERA, () -> "EN_EJECUCION".equals(estado(obraId)));
        mvc.perform(get("/obras/" + obraId).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.contrato.contratoId").value(contratoId.toString()))
                .andExpect(jsonPath("$.motivoRechazoContrato").isEmpty());
    }

    @Test
    void contratoRechazadoGuardaElMotivoYUnMensajeRepetidoNoSeVuelveAProcesar() throws Exception {
        UUID obraId = ObrasPrueba.registrar(mvc, "Obra rechazada", Equipo.nuevo());
        UUID mensajeId = UUID.randomUUID();
        String motivo = "El anticipo más la retención deben sumar menos de 100 %";

        publicar(RECHAZADO, sobre(mensajeId, "ContratoRechazado",
                "{\"obraId\": \"%s\", \"motivo\": \"%s\"}".formatted(obraId, motivo)));
        Esperar.hasta(ESPERA, () -> motivo.equals(motivoRechazo(obraId)));

        // El mismo id con otro motivo: se confirma sin hacer nada
        publicar(RECHAZADO, sobre(mensajeId, "ContratoRechazado",
                "{\"obraId\": \"%s\", \"motivo\": \"otro motivo\"}".formatted(obraId)));
        // Un mensaje posterior de la misma cola: cuando se procesa, el repetido ya pasó
        UUID testigo = ObrasPrueba.registrar(mvc, "Obra testigo", Equipo.nuevo());
        publicar(REGISTRADO, sobre(UUID.randomUUID(), "ContratoRegistrado",
                "{\"obraId\": \"%s\", \"contratoId\": \"%s\"}".formatted(testigo, UUID.randomUUID())));
        Esperar.hasta(ESPERA, () -> "EN_EJECUCION".equals(estado(testigo)));

        assertThat(motivoRechazo(obraId)).isEqualTo(motivo);
        assertThat(estado(obraId)).isEqualTo("SIN_CONTRATO");
        assertThat(jdbc.queryForObject("select count(*) from mensaje_procesado where id_mensaje = ?",
                Integer.class, mensajeId)).isEqualTo(1);
        mvc.perform(get("/obras/" + obraId).header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.estado").value("SIN_CONTRATO"))
                .andExpect(jsonPath("$.motivoRechazoContrato").value(motivo));
    }

    @Test
    void siFallaTresVecesVaAFallidosConLosEncabezados() {
        UUID mensajeId = UUID.randomUUID();
        // La obra no existe: cada intento falla
        publicar(REGISTRADO, sobre(mensajeId, "ContratoRegistrado",
                "{\"obraId\": \"%s\", \"contratoId\": \"%s\"}".formatted(UUID.randomUUID(), UUID.randomUUID())));

        Message fallido = recibirFallido(mensajeId);
        Map<String, Object> encabezados = fallido.getMessageProperties().getHeaders();
        assertThat(((Number) encabezados.get("x-intentos")).intValue()).isEqualTo(3);
        assertThat(encabezados.get("x-error").toString()).contains("No existe la obra");
        assertThat(encabezados.get("x-cola-origen")).hasToString("obras");
        assertThat(encabezados.get("x-clave-origen")).hasToString(REGISTRADO);
        assertThat(fallido.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(fallido.getBody(), StandardCharsets.UTF_8)).contains(mensajeId.toString());
        assertThat(jdbc.queryForObject("select count(*) from mensaje_procesado where id_mensaje = ?",
                Integer.class, mensajeId)).isZero();
    }

    @Test
    void unMensajeInvalidoVaDirectoAFallidos() {
        UUID noJson = UUID.randomUUID();
        publicar(REGISTRADO, noJson, "esto no es JSON");
        assertDirectoAFallidos(recibirFallido(noJson), REGISTRADO);

        UUID versionDos = UUID.randomUUID();
        publicar(REGISTRADO, versionDos, sobre(versionDos, "ContratoRegistrado", "{}").replace("\"version\": 1",
                "\"version\": 2"));
        assertDirectoAFallidos(recibirFallido(versionDos), REGISTRADO);

        UUID incompleto = UUID.randomUUID();
        publicar(RECHAZADO, sobre(incompleto, "ContratoRechazado",
                "{\"obraId\": \"%s\"}".formatted(UUID.randomUUID())));
        assertDirectoAFallidos(recibirFallido(incompleto), RECHAZADO);

        UUID sinConsumidor = UUID.randomUUID();
        publicar(REGISTRADO, sobre(sinConsumidor, "SaldoReservado", "{}"));
        Message fallido = recibirFallido(sinConsumidor);
        assertDirectoAFallidos(fallido, REGISTRADO);
        assertThat(fallido.getMessageProperties().getHeaders().get("x-error").toString()).contains("SaldoReservado");
    }

    private static void assertDirectoAFallidos(Message fallido, String clave) {
        Map<String, Object> encabezados = fallido.getMessageProperties().getHeaders();
        assertThat(((Number) encabezados.get("x-intentos")).intValue()).isEqualTo(1);
        assertThat(encabezados.get("x-cola-origen")).hasToString("obras");
        assertThat(encabezados.get("x-clave-origen")).hasToString(clave);
        assertThat(encabezados.get("x-error")).isNotNull();
    }

    private static String sobre(UUID id, String tipo, String datos) {
        return """
                {"id": "%s", "tipo": "%s", "version": 1, "ocurridoEn": "%s", "origen": "finanzas",
                 "correlacionId": "%s", "datos": %s}""".formatted(id, tipo, Instant.now(), UUID.randomUUID(), datos);
    }

    private void publicar(String clave, String sobre) {
        UUID id = UUID.fromString(sobre.substring(sobre.indexOf("\"id\": \"") + 7, sobre.indexOf("\"id\": \"") + 43));
        publicar(clave, id, sobre);
    }

    private void publicar(String clave, UUID id, String cuerpo) {
        rabbit.send("plataforma", clave, MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(id.toString())
                .build());
    }

    /** Lee plataforma.fallidos hasta encontrar el mensaje con ese id (los demás son de otras pruebas). */
    private Message recibirFallido(UUID mensajeId) {
        Instant fin = Instant.now().plus(ESPERA);
        while (Instant.now().isBefore(fin)) {
            Message mensaje = rabbit.receive("plataforma.fallidos", 500);
            if (mensaje != null && mensajeId.toString().equals(mensaje.getMessageProperties().getMessageId())) {
                return mensaje;
            }
        }
        throw new AssertionError("No llegó a plataforma.fallidos el mensaje " + mensajeId);
    }

    private String estado(UUID obraId) {
        return jdbc.queryForObject("select estado from obra where id = ?", String.class, obraId);
    }

    private String motivoRechazo(UUID obraId) {
        return jdbc.queryForObject("select motivo_rechazo_contrato from obra where id = ?", String.class, obraId);
    }

    private static String admin() {
        return TokensPrueba.valido(ObrasPrueba.ADMIN, "admin");
    }
}
